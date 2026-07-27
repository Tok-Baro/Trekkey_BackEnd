package com.api.trekkey.domain.submission.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SubmissionTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 24, 12, 0);

    @Test
    @DisplayName("철회된 제출물은 엔티티를 직접 호출해도 다시 제출할 수 없다")
    void submit_rejectsWithdrawnState() {
        Submission submission = submission(SubmissionStatus.WITHDRAWN);

        assertThat(submission.submit(NOW)).isFalse();
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.WITHDRAWN);
        assertThat(submission.getSubmittedAt()).isNull();
    }

    @Test
    @DisplayName("제출 및 최종 확정 시각은 null일 수 없다")
    void transitionTimestamps_rejectNull() {
        Submission draft = submission(SubmissionStatus.DRAFT);
        Submission submitted = submission(SubmissionStatus.SUBMITTED);

        assertThat(draft.submit(null)).isFalse();
        assertThat(draft.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(submitted.finalizeAt(null)).isFalse();
        assertThat(submitted.getFinalizedAt()).isNull();
    }

    @Test
    @DisplayName("제출된 작품만 리뷰 편입 시각으로 최종 확정할 수 있다")
    void finalizeAt_onlyFinalizesSubmittedSubmission() {
        Submission draft = submission(SubmissionStatus.DRAFT);
        Submission submitted = submission(SubmissionStatus.SUBMITTED);

        assertThat(draft.finalizeAt(NOW)).isFalse();
        assertThat(draft.getFinalizedAt()).isNull();
        assertThat(submitted.finalizeAt(NOW)).isTrue();
        assertThat(submitted.getFinalizedAt()).isEqualTo(NOW);
        assertThat(submitted.finalizeAt(NOW.plusMinutes(1))).isFalse();
        assertThat(submitted.getFinalizedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("최종 확정된 제출물은 수정하거나 철회할 수 없다")
    void finalizedSubmission_rejectsMutation() {
        Submission submission = submission(SubmissionStatus.SUBMITTED);
        assertThat(submission.finalizeAt(NOW)).isTrue();

        assertThat(submission.updateDraftTitle("변경 작품")).isFalse();
        assertThat(submission.withdraw()).isFalse();
        assertThat(submission.getTitle()).isEqualTo("AI 캠퍼스");
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
    }

    @Test
    @DisplayName("제출되지 않은 초안은 철회할 수 없다")
    void withdraw_rejectsDraftState() {
        Submission submission = submission(SubmissionStatus.DRAFT);

        assertThat(submission.withdraw()).isFalse();
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
    }

    @Test
    @DisplayName("제출된 작품은 최종 확정 전 초안으로 다시 열 수 있다")
    void reopenDraft_reopensSubmittedSubmission() {
        Submission submission = submission(SubmissionStatus.SUBMITTED);
        ReflectionTestUtils.setField(submission, "submittedAt", NOW);

        assertThat(submission.reopenDraft()).isTrue();
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(submission.getSubmittedAt()).isEqualTo(NOW);
    }

    private Submission submission(SubmissionStatus status) {
        return Submission.builder()
                .title("AI 캠퍼스")
                .status(status)
                .build();
    }
}
