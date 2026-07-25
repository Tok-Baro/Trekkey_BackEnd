package com.api.trekkey.domain.contest.publicapi.service;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestLike;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestLikeRepository;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchRes;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchStatus;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestServiceImpl implements ContestService {

    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;
    private final ContestLikeRepository contestLikeRepository;
    private final UserRepository userRepository;

    @Override
    public List<ContestSearchRes> searchContests(Long userId, String keyword, ContestSearchStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        Set<ContestStatus> contestStatuses = switch (status) {
            case OPEN -> Set.of(ContestStatus.APPLICATION_OPEN);
            case ALL -> Set.of(
                    ContestStatus.APPLICATION_OPEN,
                    ContestStatus.REVIEWING,
                    ContestStatus.AWARDED);
            case CLOSED -> Set.of(ContestStatus.REVIEWING, ContestStatus.AWARDED);
        };
        List<Contest> contests = contestRepository.searchContests(
                user.getOrganization().getId(),
                normalizedKeyword,
                contestStatuses);

        if (contests.isEmpty()) {
            return List.of();
        }

        List<Long> contestIds = contests.stream()
                .map(Contest::getId)
                .toList();
        Map<Long, ContestStage> submissionStagesByContest = contestStageRepository
                .findAllByContestIdInAndStageTypeOrderBySequenceNoAsc(
                        contestIds,
                        StageType.SUBMISSION)
                .stream()
                .collect(Collectors.toMap(
                        stage -> stage.getContest().getId(),
                        stage -> stage,
                        (firstStage, ignored) -> firstStage));
        Map<Long, Long> likeCounts = contestLikeRepository.findAllByContestIdIn(contestIds)
                .stream()
                .collect(Collectors.groupingBy(
                        contestLike -> contestLike.getContest().getId(),
                        Collectors.counting()));

        return contests.stream()
                .map(contest -> {
                    ContestStage submissionStage = submissionStagesByContest.get(contest.getId());
                    return ContestSearchRes.from(
                            contest,
                            submissionStage == null ? null : submissionStage.getEndsAt(),
                            likeCounts.getOrDefault(contest.getId(), 0L));
                })
                .toList();
    }

    @Override
    public ContestDetailRes getContestDetail(Long userId, String publicId) {
        // 참가자에게 준비 중인 대회가 노출되지 않도록 공개 가능한 상태만 조회한다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));

        Contest contest = contestRepository.findByPublicIdAndOrganizationIdAndStatusIn(
                        publicId,
                        user.getOrganization().getId(),
                        Set.of(
                                ContestStatus.APPLICATION_OPEN,
                                ContestStatus.REVIEWING,
                                ContestStatus.AWARDED))
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));
        List<ContestStage> stages = contestStageRepository
                .findAllByContestIdAndStageTypeInOrderBySequenceNoAsc(
                        contest.getId(),
                        Set.of(StageType.APPLICATION, StageType.SUBMISSION));
        long likeCount = contestLikeRepository.countByContestId(contest.getId());

        return ContestDetailRes.from(contest, stages, likeCount);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public long toggleLike(Long userId, String contestPublicId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));

        //자기 학교 대회만 좋아요 가능 — 검색과 동일한 조직 스코프
        Contest contest = contestRepository.findByPublicId(contestPublicId)
                .filter(found -> found.getOrganization().getId().equals(user.getOrganization().getId()))
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));

        //이미 눌렀으면 취소, 아니면 등록 (uk_contest_like_contest_user가 동시 요청 방어)
        contestLikeRepository.findByContestIdAndUserId(contest.getId(), user.getId())
                .ifPresentOrElse(
                        contestLikeRepository::delete,
                        () -> contestLikeRepository.save(ContestLike.builder()
                                .contest(contest)
                                .user(user)
                                .build()));

        return contestLikeRepository.countByContestId(contest.getId());
    }
}
