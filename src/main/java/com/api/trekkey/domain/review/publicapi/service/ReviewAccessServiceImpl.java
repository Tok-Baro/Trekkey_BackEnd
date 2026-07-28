package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.publicapi.web.dto.response.ReviewAccessRes;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewAccessServiceImpl implements ReviewAccessService {

    private final ReviewLinkAuthenticator reviewLinkAuthenticator;
    private final Clock clock;

    @Override
    public ReviewAccessRes verifyAccess(ReviewAccessReq req) {
        String rawToken = req == null ? null : req.token();
        ContestJudge judge =
                reviewLinkAuthenticator.authenticate(
                        rawToken,
                        LocalDateTime.now(clock));
        return ReviewAccessRes.from(judge);
    }
}
