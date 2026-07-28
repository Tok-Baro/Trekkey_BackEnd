package com.api.trekkey.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewRoundEntryTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 28, 15, 0);

    @Test
    @DisplayName("진행 중인 대상은 규칙 판정 결과로 한 번만 확정할 수 있다")
    void finalizeByRule_transitionsInReviewEntry() {
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build();

        assertThat(entry.finalizeByRule(
                new BigDecimal("91.25"),
                1,
                ReviewRoundEntryStatus.SELECTED,
                NOW
        )).isTrue();
        assertThat(entry.getDecisionType())
                .isEqualTo(ReviewDecisionType.RULE);
        assertThat(entry.getFinalScore())
                .isEqualByComparingTo("91.25");
        assertThat(entry.getRankNo()).isEqualTo(1);
        assertThat(entry.isFinalized()).isTrue();
        assertThat(entry.finalizeByRule(
                BigDecimal.TEN,
                2,
                ReviewRoundEntryStatus.NOT_SELECTED,
                NOW.plusMinutes(1)
        )).isFalse();
    }

    @Test
    @DisplayName("수동 판정에는 관리자와 사유가 모두 필요하다")
    void finalizeManually_requiresActorAndReason() {
        User admin = User.builder().id(10L).build();
        ReviewRoundEntry valid = ReviewRoundEntry.builder()
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build();
        ReviewRoundEntry missingReason = ReviewRoundEntry.builder()
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build();

        assertThat(valid.finalizeManually(
                new BigDecimal("80.00"),
                2,
                ReviewRoundEntryStatus.NOT_SELECTED,
                admin,
                "근거 자료가 부족함",
                NOW
        )).isTrue();
        assertThat(valid.getDecidedByUser()).isSameAs(admin);
        assertThat(valid.getDecisionReason())
                .isEqualTo("근거 자료가 부족함");
        assertThat(valid.getDecisionType())
                .isEqualTo(ReviewDecisionType.MANUAL);

        assertThat(missingReason.finalizeManually(
                new BigDecimal("80.00"),
                2,
                ReviewRoundEntryStatus.NOT_SELECTED,
                admin,
                " ",
                NOW
        )).isFalse();
    }

    @Test
    @DisplayName("심사 없는 수동 라운드는 점수 없이 명시 순위와 관리자 판정으로 확정한다")
    void finalizeManually_supportsDecisionWithoutScore() {
        User admin = User.builder().id(10L).build();
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build();

        assertThat(entry.finalizeManually(
                null,
                1,
                ReviewRoundEntryStatus.SELECTED,
                admin,
                "관리자 위원회 선정",
                NOW
        )).isTrue();

        assertThat(entry.getFinalScore()).isNull();
        assertThat(entry.getRankNo()).isEqualTo(1);
        assertThat(entry.getDecisionType())
                .isEqualTo(ReviewDecisionType.MANUAL);
        assertThat(entry.isFinalized()).isTrue();
    }
}
