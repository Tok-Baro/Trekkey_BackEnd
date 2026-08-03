package com.api.trekkey.domain.submission.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubmissionTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 24, 12, 0);

    @Test
    @DisplayName("재제출하면 같은 제출물의 제목과 최근 제출 시각을 덮어쓴다")
    void overwrite_updatesCurrentSubmission() {
        Submission submission = submission(SubmissionStatus.DRAFT);

        submission.overwrite("개선된 AI 캠퍼스", NOW);

        assertThat(submission.getTitle()).isEqualTo("개선된 AI 캠퍼스");
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("제출 완료 상태와 유효한 시각이 모두 있어야 최종 확정할 수 있다")
    void finalizeAt_requiresSubmittedStateAndTimestamp() {
        Submission draft = submission(SubmissionStatus.DRAFT);
        Submission submitted = submission(SubmissionStatus.SUBMITTED);

        assertThat(draft.finalizeAt(NOW)).isFalse();
        assertThat(draft.getFinalizedAt()).isNull();
        assertThat(submitted.finalizeAt(null)).isFalse();
        assertThat(submitted.getFinalizedAt()).isNull();
    }

    @Test
    @DisplayName("제출물은 한 번만 최종 확정하며 최초 확정 시각을 유지한다")
    void finalizeAt_preservesFirstFinalizedTimestamp() {
        Submission submission = submission(SubmissionStatus.SUBMITTED);

        assertThat(submission.isFinalized()).isFalse();
        assertThat(submission.finalizeAt(NOW)).isTrue();
        assertThat(submission.isFinalized()).isTrue();
        assertThat(submission.getFinalizedAt()).isEqualTo(NOW);
        assertThat(submission.finalizeAt(NOW.plusMinutes(1))).isFalse();
        assertThat(submission.getFinalizedAt()).isEqualTo(NOW);
    }

    private Submission submission(SubmissionStatus status) {
        return Submission.builder()
                .title("AI 캠퍼스")
                .status(status)
                .submittedAt(NOW.minusDays(1))
                .build();
    }
}
