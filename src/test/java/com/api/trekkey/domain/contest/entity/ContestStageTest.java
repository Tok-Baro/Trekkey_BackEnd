package com.api.trekkey.domain.contest.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContestStageTest {

    private static final LocalDateTime START =
            LocalDateTime.of(2026, 7, 24, 9, 0);
    private static final LocalDateTime END =
            LocalDateTime.of(2026, 7, 24, 18, 0);

    @Test
    @DisplayName("OPEN 단계는 시작 시각부터 마감 직전까지만 열려 있다")
    void isOpenAt_usesStartInclusiveAndEndExclusiveWindow() {
        ContestStage stage = submissionStage(StageStatus.OPEN, END);

        assertThat(stage.isOpenAt(START)).isTrue();
        assertThat(stage.isOpenAt(END.minusNanos(1))).isTrue();
        assertThat(stage.isOpenAt(END)).isFalse();
    }

    @Test
    @DisplayName("단계 시간이 맞아도 상태가 OPEN이 아니면 열려 있지 않다")
    void isOpenAt_requiresOpenStatus() {
        ContestStage stage = submissionStage(StageStatus.PREPARING, END);

        assertThat(stage.isOpenAt(START.plusHours(1))).isFalse();
    }

    @Test
    @DisplayName("제출 단계는 마감 시각이 있어야 시작할 수 있다")
    void hasValidConfigurationForOpening_requiresSubmissionDeadline() {
        ContestStage stage = submissionStage(StageStatus.PREPARING, null);

        assertThat(stage.hasValidConfigurationForOpening()).isFalse();
    }

    private ContestStage submissionStage(
            StageStatus status,
            LocalDateTime endsAt
    ) {
        return ContestStage.builder()
                .name("제출")
                .stageType(StageType.SUBMISSION)
                .sequenceNo(1)
                .status(status)
                .startsAt(START)
                .endsAt(endsAt)
                .build();
    }
}
