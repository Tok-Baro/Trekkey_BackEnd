package com.api.trekkey.domain.review.support;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewLinkStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewLinkAuthenticator {

    private static final Pattern REVIEW_TOKEN_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{43}");

    private final ContestJudgeRepository contestJudgeRepository;
    private final ReviewLinkTokenManager reviewLinkTokenManager;

    public ContestJudge authenticate(String rawToken, LocalDateTime now) {
        if (rawToken == null || !REVIEW_TOKEN_PATTERN.matcher(rawToken).matches()) {
            throw new CustomException(ReviewErrorResponseCode.REVIEW_LINK_INVALID);
        }

        ContestJudge judge = contestJudgeRepository.findByReviewTokenHashForShare(
                        reviewLinkTokenManager.hash(rawToken))
                .orElseThrow(() -> new CustomException(
                        ReviewErrorResponseCode.REVIEW_LINK_INVALID));

        ReviewLinkStatus linkStatus = judge.getReviewLinkStatus(now);
        if (linkStatus != ReviewLinkStatus.ACTIVE) {
            throw new CustomException(ReviewErrorResponseCode.REVIEW_LINK_INVALID);
        }
        User linkedUser = judge.getUser();
        if (linkedUser != null
                && (linkedUser.getStatus() != UserStatus.ACTIVE
                || !linkedUser.getOrganization().getId().equals(
                judge.getContest().getOrganization().getId()))) {
            throw new CustomException(
                    ReviewErrorResponseCode.REVIEW_LINK_INVALID);
        }

        return judge;
    }
}
