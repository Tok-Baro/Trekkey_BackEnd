package com.api.trekkey.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewAssignmentTest {

    private static final LocalDateTime ASSIGNED_AT =
            LocalDateTime.of(2026, 7, 24, 12, 0);
    private static final LocalDateTime DUE_AT =
            LocalDateTime.of(2026, 7, 25, 12, 0);

    @Test
    @DisplayName("ASSIGNED 배정을 완료하면 COMPLETED 상태와 완료 시각을 기록한다")
    void complete_transitionsAssignedToCompleted() {
        ReviewAssignment assignment =
                assignment(ReviewAssignmentStatus.ASSIGNED);
        LocalDateTime completedAt = ASSIGNED_AT.plusHours(2);

        boolean changed = assignment.complete(completedAt);

        assertThat(changed).isTrue();
        assertThat(assignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.COMPLETED);
        assertThat(assignment.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("ASSIGNED 배정을 취소하면 CANCELED 상태가 된다")
    void cancel_transitionsAssignedToCanceled() {
        ReviewAssignment assignment =
                assignment(ReviewAssignmentStatus.ASSIGNED);

        boolean changed = assignment.cancel();

        assertThat(changed).isTrue();
        assertThat(assignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.CANCELED);
        assertThat(assignment.getAssignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(assignment.getDueAt()).isEqualTo(DUE_AT);
    }

    @Test
    @DisplayName("CANCELED 배정을 재배정하면 ASSIGNED로 돌아가고 시각을 갱신한다")
    void reassign_transitionsCanceledToAssigned() {
        ReviewAssignment assignment = ReviewAssignment.builder()
                .status(ReviewAssignmentStatus.CANCELED)
                .assignedAt(ASSIGNED_AT)
                .dueAt(DUE_AT)
                .completedAt(ASSIGNED_AT.plusHours(1))
                .build();
        LocalDateTime reassignedAt = ASSIGNED_AT.plusDays(1);
        LocalDateTime newDueAt = DUE_AT.plusDays(1);

        boolean changed = assignment.reassign(
                reassignedAt,
                newDueAt
        );

        assertThat(changed).isTrue();
        assertThat(assignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
        assertThat(assignment.getAssignedAt()).isEqualTo(reassignedAt);
        assertThat(assignment.getDueAt()).isEqualTo(newDueAt);
        assertThat(assignment.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("ASSIGNED가 아닌 배정은 완료할 수 없고 완료 시각도 바뀌지 않는다")
    void complete_rejectsInvalidStatus() {
        LocalDateTime existingCompletedAt = ASSIGNED_AT.plusHours(1);
        ReviewAssignment canceled = ReviewAssignment.builder()
                .status(ReviewAssignmentStatus.CANCELED)
                .assignedAt(ASSIGNED_AT)
                .dueAt(DUE_AT)
                .build();
        ReviewAssignment completed = ReviewAssignment.builder()
                .status(ReviewAssignmentStatus.COMPLETED)
                .assignedAt(ASSIGNED_AT)
                .dueAt(DUE_AT)
                .completedAt(existingCompletedAt)
                .build();

        assertThat(canceled.complete(ASSIGNED_AT.plusHours(2)))
                .isFalse();
        assertThat(completed.complete(ASSIGNED_AT.plusHours(2)))
                .isFalse();

        assertThat(canceled.getStatus())
                .isEqualTo(ReviewAssignmentStatus.CANCELED);
        assertThat(canceled.getCompletedAt()).isNull();
        assertThat(completed.getStatus())
                .isEqualTo(ReviewAssignmentStatus.COMPLETED);
        assertThat(completed.getCompletedAt())
                .isEqualTo(existingCompletedAt);
    }

    @Test
    @DisplayName("완료 시각이 없으면 ASSIGNED 배정도 완료할 수 없다")
    void complete_rejectsNullCompletedAt() {
        ReviewAssignment assignment =
                assignment(ReviewAssignmentStatus.ASSIGNED);

        boolean changed = assignment.complete(null);

        assertThat(changed).isFalse();
        assertThat(assignment.getStatus())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
        assertThat(assignment.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("CANCELED 또는 COMPLETED 배정은 다시 취소할 수 없다")
    void cancel_rejectsInvalidStatus() {
        ReviewAssignment canceled =
                assignment(ReviewAssignmentStatus.CANCELED);
        ReviewAssignment completed =
                assignment(ReviewAssignmentStatus.COMPLETED);

        assertThat(canceled.cancel()).isFalse();
        assertThat(completed.cancel()).isFalse();

        assertThat(canceled.getStatus())
                .isEqualTo(ReviewAssignmentStatus.CANCELED);
        assertThat(completed.getStatus())
                .isEqualTo(ReviewAssignmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("CANCELED가 아니거나 재배정 시각이 없으면 재배정할 수 없다")
    void reassign_rejectsInvalidTransition() {
        ReviewAssignment assigned =
                assignment(ReviewAssignmentStatus.ASSIGNED);
        ReviewAssignment completed =
                assignment(ReviewAssignmentStatus.COMPLETED);
        ReviewAssignment canceled =
                assignment(ReviewAssignmentStatus.CANCELED);

        assertThat(assigned.reassign(
                ASSIGNED_AT.plusDays(1),
                DUE_AT.plusDays(1)
        )).isFalse();
        assertThat(completed.reassign(
                ASSIGNED_AT.plusDays(1),
                DUE_AT.plusDays(1)
        )).isFalse();
        assertThat(canceled.reassign(null, DUE_AT.plusDays(1)))
                .isFalse();

        assertThat(assigned.getStatus())
                .isEqualTo(ReviewAssignmentStatus.ASSIGNED);
        assertThat(completed.getStatus())
                .isEqualTo(ReviewAssignmentStatus.COMPLETED);
        assertThat(canceled.getStatus())
                .isEqualTo(ReviewAssignmentStatus.CANCELED);
        assertThat(canceled.getAssignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(canceled.getDueAt()).isEqualTo(DUE_AT);
    }

    @Test
    @DisplayName("ASSIGNED 배정은 마감 전만 이용할 수 있고 now와 dueAt이 같으면 이용할 수 없다")
    void isAvailableAt_usesExclusiveDueAtBoundary() {
        ReviewAssignment assignment =
                assignment(ReviewAssignmentStatus.ASSIGNED);

        assertThat(assignment.isAvailableAt(
                DUE_AT.minusNanos(1)
        )).isTrue();
        assertThat(assignment.isAvailableAt(DUE_AT)).isFalse();
        assertThat(assignment.isAvailableAt(
                DUE_AT.plusNanos(1)
        )).isFalse();
    }

    @Test
    @DisplayName("마감 시각이 없는 ASSIGNED만 이용할 수 있다")
    void isAvailableAt_requiresAssignedStatus() {
        ReviewAssignment assignedWithoutDeadline =
                ReviewAssignment.builder()
                        .status(ReviewAssignmentStatus.ASSIGNED)
                        .assignedAt(ASSIGNED_AT)
                        .build();
        ReviewAssignment canceled =
                assignment(ReviewAssignmentStatus.CANCELED);
        ReviewAssignment completed =
                assignment(ReviewAssignmentStatus.COMPLETED);

        assertThat(assignedWithoutDeadline.isAvailableAt(DUE_AT))
                .isTrue();
        assertThat(canceled.isAvailableAt(
                DUE_AT.minusHours(1)
        )).isFalse();
        assertThat(completed.isAvailableAt(
                DUE_AT.minusHours(1)
        )).isFalse();
    }

    private ReviewAssignment assignment(
            ReviewAssignmentStatus status
    ) {
        return ReviewAssignment.builder()
                .status(status)
                .assignedAt(ASSIGNED_AT)
                .dueAt(DUE_AT)
                .completedAt(
                        status == ReviewAssignmentStatus.COMPLETED
                                ? ASSIGNED_AT.plusHours(1)
                                : null
                )
                .build();
    }
}
