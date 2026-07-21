package com.api.trekkey.domain.contest.publicapi.service;

import com.api.trekkey.domain.contest.entity.Contest;
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
        /*
            대회 단건 조회에 상태값이 들어가는 이유
            현재 publicId로 조회를 하는데 publicId로만 조회 시 상태값이 준비 중인 상태 PREPARING도 조회가 가능하기 때문임.
            이 API는 누구나 접근 가능한 API이기 때문에 위 사항을 방어해야함.
            그래서 Set으로 조회가능한 상태값을 넣어서 조회한다.
         */
        Contest contest = contestRepository.findByPublicIdAndStatusIn(
                        publicId,
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
}
