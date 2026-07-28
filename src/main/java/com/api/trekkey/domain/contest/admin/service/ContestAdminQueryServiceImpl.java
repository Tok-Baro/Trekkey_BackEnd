package com.api.trekkey.domain.contest.admin.service;

import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSearchCond;
import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSummaryRes;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestQueryRepository;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.response.PageRes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestAdminQueryServiceImpl implements ContestAdminQueryService {

    private final ContestQueryRepository contestQueryRepository;
    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestStageRepository contestStageRepository;

    @Override
    public PageRes<ContestAdminSummaryRes> getContests(Long userId, ContestAdminSearchCond cond) {
        User admin = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        Long organizationId = admin.getOrganization().getId();

        List<Contest> contests = contestQueryRepository.findAdminContests(organizationId, cond);
        long totalElements = contestQueryRepository.countAdminContests(organizationId, cond);

        List<ContestAdminSummaryRes> content = contests.stream()
                .map(ContestAdminSummaryRes::from)
                .toList();

        return PageRes.of(content, cond.page(), cond.size(), totalElements);
    }

    @Override
    public ContestDetailRes getContest(Long userId, String publicId) {
        User admin = findActiveAdmin(userId);
        Contest contest = contestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(
                        ContestErrorResponseCode.CONTEST_NOT_FOUND));
        if (!contest.getOrganization().getId()
                .equals(admin.getOrganization().getId())) {
            throw new CustomException(
                    ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }

        List<StageRes> stages = contestStageRepository
                .findAllByContestIdOrderBySequenceNoAsc(contest.getId())
                .stream()
                .filter(stage ->
                        !stage.getStageType().supportsReviewCriteria())
                .map(stage -> StageRes.from(stage, List.of()))
                .toList();
        return ContestDetailRes.of(contest, stages);
    }

    private User findActiveAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(
                        UserErrorResponseCode.USER_NOT_FOUND));
        if ((user.getRole() != UserRole.ADMIN
                && user.getRole() != UserRole.ROOT_ADMIN)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(
                    UserErrorResponseCode.USER_INVALID_TOKEN);
        }
        return user;
    }
}
