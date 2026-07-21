package com.api.trekkey.domain.contest.service;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.repository.ContestQueryRepository;
import com.api.trekkey.domain.contest.web.dto.ContestAdminSearchCond;
import com.api.trekkey.domain.contest.web.dto.ContestAdminSummaryRes;
import com.api.trekkey.domain.user.entity.User;
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
}
