package com.api.trekkey.domain.submission.publicapi.service;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionSaveReq;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionServiceImpl implements SubmissionService {

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;
    private final TeamRepository teamRepository;
    private final SubmissionRepository submissionRepository;
    private final Clock clock;
    private final EntityManager entityManager;

    @Override
    public SubmissionRes getSubmission(Long userId, String contestPublicId) {
        User participant = findActiveParticipant(userId);
        Contest contest = findContest(contestPublicId, participant);
        Team team = findTeam(contest.getId(), participant.getId());
        Submission submission = submissionRepository.findByTeamId(team.getId())
                .orElseThrow(() -> new CustomException(
                        SubmissionErrorResponseCode.SUBMISSION_NOT_FOUND));

        return SubmissionRes.from(submission);
    }

    @Override
    @Transactional
    public SubmissionRes saveDraft(
            Long userId,
            String contestPublicId,
            SubmissionSaveReq request
    ) {
        MutationContext context = findMutationContext(userId, contestPublicId);
        Submission submission = context.submission();
        if (submission == null) {
            submission = createSubmission(context.team(), request.title().trim());
        } else {
            if (!submission.isDraftEditable()) {
                throw new CustomException(
                        SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION);
            }
            submission.updateDraftTitle(request.title().trim());
            submissionRepository.flush();
        }

        return SubmissionRes.from(submission);
    }

    @Override
    @Transactional
    public SubmissionRes submit(Long userId, String contestPublicId) {
        MutationContext context = findMutationContext(userId, contestPublicId);
        Submission submission = requireSubmission(context);

        if (submission.isFinalized()
                || submission.getStatus() == SubmissionStatus.WITHDRAWN) {
            throw new CustomException(
                    SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION);
        }
        if (submission.submit(context.now())) {
            submissionRepository.flush();
        }
        return SubmissionRes.from(submission);
    }

    @Override
    @Transactional
    public SubmissionRes reopen(Long userId, String contestPublicId) {
        MutationContext context = findMutationContext(userId, contestPublicId);
        Submission submission = requireSubmission(context);

        if (submission.isFinalized()
                || submission.getStatus() == SubmissionStatus.WITHDRAWN) {
            throw new CustomException(
                    SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION);
        }
        if (submission.reopenDraft()) {
            submissionRepository.flush();
        }
        return SubmissionRes.from(submission);
    }

    @Override
    @Transactional
    public SubmissionRes withdraw(Long userId, String contestPublicId) {
        MutationContext context = findMutationContext(userId, contestPublicId);
        Submission submission = requireSubmission(context);

        if (submission.isFinalized()
                || submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new CustomException(
                    SubmissionErrorResponseCode.INVALID_SUBMISSION_STATUS_TRANSITION);
        }
        if (submission.withdraw()) {
            submissionRepository.flush();
        }
        return SubmissionRes.from(submission);
    }

    private MutationContext findMutationContext(
            Long userId,
            String contestPublicId
    ) {
        User participant = findActiveParticipant(userId);
        Contest contest = findContest(contestPublicId, participant);
        ContestStage submissionStage = findSubmissionStageForShare(contest.getId());
        entityManager.refresh(contest, LockModeType.PESSIMISTIC_READ);
        validateSubmissionWindow(
                contest,
                submissionStage,
                LocalDateTime.now(clock)
        );
        Team team = findApprovedTeamForUpdate(contest.getId(), participant.getId());
        Submission submission =
                submissionRepository.findByTeamIdForUpdate(team.getId()).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        validateSubmissionWindow(contest, submissionStage, now);

        return new MutationContext(team, submission, now);
    }

    private User findActiveParticipant(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.PARTICIPANT
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(UserErrorResponseCode.USER_INVALID_TOKEN);
        }
        return user;
    }

    private Contest findContest(String publicId, User participant) {
        return contestRepository.findByPublicIdAndOrganizationId(
                        publicId,
                        participant.getOrganization().getId())
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.CONTEST_NOT_FOUND));
    }

    private Team findTeam(Long contestId, Long participantId) {
        return teamRepository.findByContestIdAndLeaderUserId(contestId, participantId)
                .orElseThrow(() -> new CustomException(
                        SubmissionErrorResponseCode.SUBMISSION_TEAM_NOT_FOUND));
    }

    private Team findApprovedTeamForUpdate(Long contestId, Long participantId) {
        Team team = teamRepository.findByContestIdAndLeaderUserIdForUpdate(
                        contestId,
                        participantId)
                .orElseThrow(() -> new CustomException(
                        SubmissionErrorResponseCode.SUBMISSION_TEAM_NOT_FOUND));
        if (team.getStatus() != TeamStatus.APPROVED) {
            throw new CustomException(
                    SubmissionErrorResponseCode.SUBMISSION_TEAM_NOT_APPROVED);
        }
        return team;
    }

    private ContestStage findSubmissionStageForShare(Long contestId) {
        List<ContestStage> submissionStages =
                contestStageRepository.findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
                        contestId,
                        StageType.SUBMISSION);
        if (submissionStages.size() != 1
                || submissionStages.getFirst().getEndsAt() == null) {
            throw new CustomException(
                    SubmissionErrorResponseCode.SUBMISSION_STAGE_INVALID);
        }
        return submissionStages.getFirst();
    }

    private void validateSubmissionWindow(
            Contest contest,
            ContestStage submissionStage,
            LocalDateTime now
    ) {
        if ((contest.getStatus() != ContestStatus.APPLICATION_OPEN
                && contest.getStatus() != ContestStatus.REVIEWING)
                || !submissionStage.isOpenAt(now)) {
            throw new CustomException(
                    SubmissionErrorResponseCode.SUBMISSION_NOT_OPEN);
        }
    }

    private Submission requireSubmission(MutationContext context) {
        if (context.submission() == null) {
            throw new CustomException(
                    SubmissionErrorResponseCode.SUBMISSION_NOT_FOUND);
        }
        return context.submission();
    }

    private Submission createSubmission(Team team, String title) {
        try {
            return submissionRepository.saveAndFlush(Submission.builder()
                    .team(team)
                    .title(title)
                    .status(SubmissionStatus.DRAFT)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    SubmissionErrorResponseCode.SUBMISSION_ALREADY_EXISTS);
        }
    }

    private record MutationContext(
            Team team,
            Submission submission,
            LocalDateTime now
    ) {
    }
}
