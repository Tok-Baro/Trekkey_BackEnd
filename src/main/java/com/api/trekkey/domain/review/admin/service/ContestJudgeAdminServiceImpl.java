package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.support.ReviewLinkTokenManager;
import com.api.trekkey.domain.review.admin.web.dto.request.ContestJudgeCreateReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewLinkIssueReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ContestJudgeRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewJudgeProgressRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewLinkIssueRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContestJudgeAdminServiceImpl implements ContestJudgeAdminService {

    private static final String TARGET_TYPE_CONTEST_JUDGE = "CONTEST_JUDGE";

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestJudgeRepository contestJudgeRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final ReviewLinkTokenManager reviewLinkTokenManager;
    private final AdminAuditLogger adminAuditLogger;
    private final Clock clock;

    @Value("${app.front.base-url}")
    private String frontBaseUrl;

    @Override
    public ContestJudgeRes createJudge(
            Long adminUserId,
            String contestPublicId,
            ContestJudgeCreateReq req
    ) {
        User admin = findUser(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        User linkedUser = findLinkedUser(req.userId(), contest);

        if (linkedUser != null
                && contestJudgeRepository.existsByContestIdAndUserId(
                contest.getId(),
                linkedUser.getId())) {
            throw new CustomException(ReviewErrorResponseCode.CONTEST_JUDGE_DUPLICATED);
        }

        ContestJudge judge;
        try {
            judge = contestJudgeRepository.saveAndFlush(ContestJudge.builder()
                    .contest(contest)
                    .user(linkedUser)
                    .name(req.name().trim())
                    .roleLabel(req.roleLabel().trim())
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ReviewErrorResponseCode.CONTEST_JUDGE_DUPLICATED);
        }

        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.CONTEST_JUDGE_CREATE,
                TARGET_TYPE_CONTEST_JUDGE,
                judge.getId(),
                "contestId=" + contest.getId() + ", name=" + judge.getName()
        );

        return ContestJudgeRes.from(judge, LocalDateTime.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContestJudgeRes> getJudges(Long adminUserId, String contestPublicId) {
        User admin = findUser(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        LocalDateTime now = LocalDateTime.now(clock);

        return contestJudgeRepository.findAllByContestIdOrderByCreatedAtAscIdAsc(contest.getId())
                .stream()
                .map(judge -> ContestJudgeRes.from(judge, now))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewJudgeProgressRes> getJudgeProgress(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId
    ) {
        User admin = findUser(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        if (reviewRoundId != null) {
            validateReviewRound(reviewRoundId, contest);
        }

        return reviewAssignmentRepository.findJudgeProgressByContestId(
                        contest.getId(),
                        reviewRoundId,
                        LocalDateTime.now(clock)
                )
                .stream()
                .map(ReviewJudgeProgressRes::from)
                .toList();
    }

    @Override
    public void deleteJudge(
            Long adminUserId,
            String contestPublicId,
            Long judgeId
    ) {
        User admin = findUser(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        ContestJudge judge = findJudgeForUpdate(judgeId, contest.getId());
        if (reviewAssignmentRepository.existsByContestJudgeId(judgeId)) {
            throw new CustomException(
                    ReviewErrorResponseCode.CONTEST_JUDGE_HAS_ASSIGNMENTS);
        }

        try {
            contestJudgeRepository.delete(judge);
            contestJudgeRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    ReviewErrorResponseCode.CONTEST_JUDGE_HAS_ASSIGNMENTS);
        }

        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.CONTEST_JUDGE_DELETE,
                TARGET_TYPE_CONTEST_JUDGE,
                judgeId,
                "contestId=" + contest.getId()
        );
    }

    @Override
    public ReviewLinkIssueRes issueReviewLink(
            Long adminUserId,
            String contestPublicId,
            Long judgeId,
            ReviewLinkIssueReq req
    ) {
        User admin = findUser(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        LocalDateTime now = LocalDateTime.now(clock);
        if (req.expiresAt() == null || !req.expiresAt().isAfter(now)) {
            throw new CustomException(ReviewErrorResponseCode.REVIEW_LINK_EXPIRATION_INVALID);
        }

        ContestJudge judge = findJudgeForUpdate(judgeId, contest.getId());
        String rawToken = reviewLinkTokenManager.generateToken();
        judge.issueReviewLink(
                reviewLinkTokenManager.hash(rawToken),
                now,
                req.expiresAt()
        );
        contestJudgeRepository.flush();

        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.REVIEW_LINK_ISSUE,
                TARGET_TYPE_CONTEST_JUDGE,
                judge.getId(),
                "expiresAt=" + req.expiresAt()
        );

        return new ReviewLinkIssueRes(
                judge.getId(),
                reviewPageUrl() + "#token=" + rawToken,
                judge.getTokenExpiresAt()
        );
    }

    @Override
    public void revokeReviewLink(Long adminUserId, String contestPublicId, Long judgeId) {
        User admin = findUser(adminUserId);
        Contest contest = findContest(contestPublicId, admin);
        ContestJudge judge = findJudgeForUpdate(judgeId, contest.getId());

        if (!judge.revokeReviewLink(LocalDateTime.now(clock))) {
            return;
        }

        adminAuditLogger.log(
                admin.getId(),
                admin.getOrganization().getId(),
                AuditAction.REVIEW_LINK_REVOKE,
                TARGET_TYPE_CONTEST_JUDGE,
                judge.getId(),
                "contestId=" + contest.getId()
        );
    }

    private User findUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        if ((user.getRole() != UserRole.ADMIN
                && user.getRole() != UserRole.ROOT_ADMIN)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(
                    UserErrorResponseCode.USER_INVALID_TOKEN);
        }
        return user;
    }

    private Contest findContest(String publicId, User admin) {
        Contest contest = contestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));
        if (!contest.getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }
        return contest;
    }

    private User findLinkedUser(Long userId, Contest contest) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.CONTEST_JUDGE_USER_INVALID));
        if (!user.getOrganization().getId().equals(contest.getOrganization().getId())
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(ReviewErrorResponseCode.CONTEST_JUDGE_USER_INVALID);
        }
        return user;
    }

    private ContestJudge findJudgeForUpdate(Long judgeId, Long contestId) {
        return contestJudgeRepository.findByIdAndContestIdForUpdate(judgeId, contestId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.CONTEST_JUDGE_NOT_FOUND));
    }

    private void validateReviewRound(Long reviewRoundId, Contest contest) {
        ReviewRound reviewRound = reviewRoundRepository.findById(reviewRoundId)
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND));
        if (!reviewRound.getContest().getId().equals(contest.getId())) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_ROUND_NOT_FOUND);
        }
    }

    private String reviewPageUrl() {
        if (frontBaseUrl.endsWith("/")) {
            return frontBaseUrl + "judge/review";
        }
        return frontBaseUrl + "/judge/review";
    }
}
