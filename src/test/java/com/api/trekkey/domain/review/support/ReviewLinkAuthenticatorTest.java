package com.api.trekkey.domain.review.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewLinkAuthenticatorTest {

    private static final String RAW_TOKEN = "a".repeat(43);
    private static final String TOKEN_HASH = "b".repeat(64);

    @Mock
    private ContestJudgeRepository contestJudgeRepository;

    @Mock
    private ReviewLinkTokenManager reviewLinkTokenManager;

    private ReviewLinkAuthenticator authenticator;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        authenticator =
                new ReviewLinkAuthenticator(contestJudgeRepository, reviewLinkTokenManager);
        now = LocalDateTime.of(2026, 7, 24, 12, 0);
    }

    @Test
    @DisplayName("활성 심사 링크를 확인하면 심사위원을 반환한다")
    void authenticate_returnsJudge() {
        ContestJudge judge = activeJudge();
        given(reviewLinkTokenManager.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);
        given(contestJudgeRepository.findByReviewTokenHashForShare(TOKEN_HASH))
                .willReturn(Optional.of(judge));

        ContestJudge result = authenticator.authenticate(RAW_TOKEN, now);

        assertThat(result).isSameAs(judge);
    }

    @Test
    @DisplayName("43자리 URL-safe 형식이 아닌 토큰은 해시하지 않고 거부한다")
    void authenticate_rejectsMalformedTokenBeforeHashing() {
        assertThatThrownBy(() -> authenticator.authenticate(null, now))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_LINK_INVALID);

        assertThatThrownBy(() -> authenticator.authenticate("raw token", now))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_LINK_INVALID);

        verifyNoInteractions(reviewLinkTokenManager, contestJudgeRepository);
    }

    @Test
    @DisplayName("저장된 해시와 일치하지 않는 토큰은 거부한다")
    void authenticate_rejectsUnknownToken() {
        given(reviewLinkTokenManager.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);
        given(contestJudgeRepository.findByReviewTokenHashForShare(TOKEN_HASH))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate(RAW_TOKEN, now))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_LINK_INVALID);
    }

    @Test
    @DisplayName("폐기된 심사 링크는 접근할 수 없다")
    void authenticate_rejectsRevokedToken() {
        ContestJudge judge = activeJudge();
        judge.revokeReviewLink(now.minusMinutes(1));
        given(reviewLinkTokenManager.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);
        given(contestJudgeRepository.findByReviewTokenHashForShare(TOKEN_HASH))
                .willReturn(Optional.of(judge));

        assertThatThrownBy(() -> authenticator.authenticate(RAW_TOKEN, now))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_LINK_INVALID);
    }

    @Test
    @DisplayName("만료 시각과 현재 시각이 같으면 심사 링크가 만료된 것으로 처리한다")
    void authenticate_rejectsExpiredTokenAtBoundary() {
        ContestJudge judge = judge();
        judge.issueReviewLink(TOKEN_HASH, now.minusDays(1), now);
        given(reviewLinkTokenManager.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);
        given(contestJudgeRepository.findByReviewTokenHashForShare(TOKEN_HASH))
                .willReturn(Optional.of(judge));

        assertThatThrownBy(() -> authenticator.authenticate(RAW_TOKEN, now))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_LINK_INVALID);
    }

    @Test
    @DisplayName("연결된 내부 사용자가 비활성화되면 기존 심사 링크도 거부한다")
    void authenticate_rejectsInactiveLinkedUser() {
        Organization organization =
                org.mockito.Mockito.mock(Organization.class);
        User linkedUser = User.builder()
                .organization(organization)
                .name("이교수")
                .email("judge@test.com")
                .password("encoded")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.FACULTY)
                .status(UserStatus.INACTIVE)
                .build();
        Contest contest = Contest.builder()
                .organization(organization)
                .publicId("contest-public-id")
                .title("AI 공모전")
                .build();
        ContestJudge judge = ContestJudge.builder()
                .contest(contest)
                .user(linkedUser)
                .name("이교수")
                .roleLabel("교내 심사위원")
                .build();
        judge.issueReviewLink(
                TOKEN_HASH,
                now.minusDays(1),
                now.plusDays(1)
        );
        given(reviewLinkTokenManager.hash(RAW_TOKEN))
                .willReturn(TOKEN_HASH);
        given(contestJudgeRepository
                .findByReviewTokenHashForShare(TOKEN_HASH))
                .willReturn(Optional.of(judge));

        assertThatThrownBy(() ->
                authenticator.authenticate(RAW_TOKEN, now))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_LINK_INVALID);
    }

    private ContestJudge activeJudge() {
        ContestJudge judge = judge();
        judge.issueReviewLink(
                TOKEN_HASH,
                now.minusDays(1),
                now.plusDays(1)
        );
        return judge;
    }

    private ContestJudge judge() {
        Contest contest = Contest.builder()
                .publicId("contest-public-id")
                .title("AI 공모전")
                .build();
        ReflectionTestUtils.setField(contest, "id", 100L);

        ContestJudge judge = ContestJudge.builder()
                .contest(contest)
                .name("김심사")
                .roleLabel("외부 전문가")
                .build();
        ReflectionTestUtils.setField(judge, "id", 200L);
        return judge;
    }
}
