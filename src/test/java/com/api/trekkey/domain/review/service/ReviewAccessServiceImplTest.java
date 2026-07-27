package com.api.trekkey.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAccessRes;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewAccessServiceImplTest {

    @Mock
    private ReviewLinkAuthenticator reviewLinkAuthenticator;

    @Test
    @DisplayName("심사 링크 확인 응답에는 심사위원과 대회의 안전한 표시 정보만 포함한다")
    void verifyAccess_returnsSafeJudgeContext() {
        Contest contest = Contest.builder()
                .publicId("contest-public-id")
                .title("AI 공모전")
                .build();
        ContestJudge judge = ContestJudge.builder()
                .contest(contest)
                .name("김심사")
                .roleLabel("외부 전문가")
                .tokenExpiresAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .build();
        given(reviewLinkAuthenticator.authenticate(
                org.mockito.ArgumentMatchers.eq("a".repeat(43)),
                any(LocalDateTime.class)
        )).willReturn(judge);

        ReviewAccessServiceImpl service =
                new ReviewAccessServiceImpl(reviewLinkAuthenticator);
        ReviewAccessRes response =
                service.verifyAccess(new ReviewAccessReq("a".repeat(43)));

        assertThat(response.judgeName()).isEqualTo("김심사");
        assertThat(response.contestPublicId()).isEqualTo("contest-public-id");
        assertThat(response.contestTitle()).isEqualTo("AI 공모전");
        assertThat(response.getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("judgeId", "token", "reviewTokenHash", "userId");
    }
}
