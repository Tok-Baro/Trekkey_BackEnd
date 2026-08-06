package com.api.trekkey.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.team.entity.Team;
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
    @DisplayName("ASSIGNED 배정의 마감 시각을 변경할 수 있다")
    void updateDueAt_updatesAssignedDeadline() {
        ReviewAssignment assignment =
                assignment(ReviewAssignmentStatus.ASSIGNED);
        LocalDateTime newDueAt = DUE_AT.plusHours(1);

        boolean changed = assignment.updateDueAt(newDueAt);

        assertThat(changed).isTrue();
        assertThat(assignment.getDueAt()).isEqualTo(newDueAt);
    }

    @Test
    @DisplayName("ASSIGNED가 아니거나 마감 시각이 없으면 변경하지 않는다")
    void updateDueAt_rejectsInvalidStateOrNullDeadline() {
        ReviewAssignment canceled =
                assignment(ReviewAssignmentStatus.CANCELED);
        ReviewAssignment completed =
                assignment(ReviewAssignmentStatus.COMPLETED);
        ReviewAssignment assigned =
                assignment(ReviewAssignmentStatus.ASSIGNED);

        assertThat(canceled.updateDueAt(DUE_AT.plusHours(1)))
                .isFalse();
        assertThat(completed.updateDueAt(DUE_AT.plusHours(1)))
                .isFalse();
        assertThat(assigned.updateDueAt(null)).isFalse();

        assertThat(canceled.getDueAt()).isEqualTo(DUE_AT);
        assertThat(completed.getDueAt()).isEqualTo(DUE_AT);
        assertThat(assigned.getDueAt()).isEqualTo(DUE_AT);
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

    @Test
    @DisplayName("심사위원에게는 열린 라운드의 이용 가능한 배정만 표시한다")
    void isVisibleToJudgeAt_appliesRoundAndAssignmentWindow() {
        LocalDateTime now = ASSIGNED_AT.plusHours(1);
        ReviewAssignment assigned = visibleAssignment(
                ReviewRoundStatus.OPEN,
                ReviewAssignmentStatus.ASSIGNED,
                now.plusHours(1)
        );
        ReviewAssignment overdue = visibleAssignment(
                ReviewRoundStatus.OPEN,
                ReviewAssignmentStatus.ASSIGNED,
                now
        );
        ReviewAssignment completed = visibleAssignment(
                ReviewRoundStatus.OPEN,
                ReviewAssignmentStatus.COMPLETED,
                now.minusMinutes(1)
        );
        ReviewAssignment finalized = visibleAssignment(
                ReviewRoundStatus.FINALIZED,
                ReviewAssignmentStatus.COMPLETED,
                now.plusHours(1)
        );

        assertThat(assigned.isVisibleToJudgeAt(now)).isTrue();
        assertThat(overdue.isVisibleToJudgeAt(now)).isFalse();
        assertThat(completed.isVisibleToJudgeAt(now)).isTrue();
        assertThat(finalized.isVisibleToJudgeAt(now)).isFalse();
    }

    @Test
    @DisplayName("심사위원·라운드·제출 팀의 대회가 다르면 배정을 노출하지 않는다")
    void isVisibleToJudgeAt_rejectsInconsistentContestScope() {
        LocalDateTime now = ASSIGNED_AT.plusHours(1);
        Contest judgeContest = Contest.builder().id(1L).build();
        Contest roundContest = Contest.builder().id(2L).build();
        Contest teamContest = Contest.builder().id(3L).build();
        ReviewAssignment assignment = visibleAssignment(
                judgeContest,
                roundContest,
                teamContest,
                ReviewRoundStatus.OPEN,
                ReviewAssignmentStatus.ASSIGNED,
                now.plusHours(1));

        assertThat(assignment.isVisibleToJudgeAt(now)).isFalse();
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

    private ReviewAssignment visibleAssignment(
            ReviewRoundStatus roundStatus,
            ReviewAssignmentStatus assignmentStatus,
            LocalDateTime dueAt
    ) {
        Contest contest = Contest.builder().id(1L).build();
        return visibleAssignment(
                contest,
                contest,
                contest,
                roundStatus,
                assignmentStatus,
                dueAt);
    }

    private ReviewAssignment visibleAssignment(
            Contest judgeContest,
            Contest roundContest,
            Contest teamContest,
            ReviewRoundStatus roundStatus,
            ReviewAssignmentStatus assignmentStatus,
            LocalDateTime dueAt
    ) {
        ReviewRound round = ReviewRound.builder()
                .contest(roundContest)
                .status(roundStatus)
                .startsAt(ASSIGNED_AT.minusHours(1))
                .endsAt(DUE_AT.plusHours(1))
                .build();
        Team team = Team.builder()
                .contest(teamContest)
                .build();
        Submission submission = Submission.builder()
                .team(team)
                .build();
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(round)
                .submission(submission)
                .build();
        return ReviewAssignment.builder()
                .contestJudge(ContestJudge.builder()
                        .contest(judgeContest)
                        .build())
                .reviewRoundEntry(entry)
                .status(assignmentStatus)
                .assignedAt(ASSIGNED_AT)
                .dueAt(dueAt)
                .completedAt(
                        assignmentStatus
                                == ReviewAssignmentStatus.COMPLETED
                                ? ASSIGNED_AT.plusMinutes(30)
                                : null)
                .build();
    }
}
