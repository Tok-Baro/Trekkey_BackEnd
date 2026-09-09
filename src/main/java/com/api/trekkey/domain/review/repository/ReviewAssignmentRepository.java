package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewAssignmentRepository
        extends JpaRepository<ReviewAssignment, Long> {

    interface JudgeProgressProjection {

        Long getJudgeId();

        String getJudgeName();

        Long getAssignedCount();

        Long getCompletedCount();

        Long getPendingCount();

        Long getOverdueCount();
    }

    interface ReviewSubmissionScope {

        Long getReviewRoundId();

        Long getReviewRoundEntryId();
    }

    @Query("""
            select reviewRound.id as reviewRoundId,
                   entry.id as reviewRoundEntryId
            from ReviewAssignment assignment
            join assignment.contestJudge judge
            join assignment.reviewRoundEntry entry
            join entry.reviewRound reviewRound
            where assignment.id = :assignmentId
              and judge.id = :judgeId
            """)
    Optional<ReviewSubmissionScope> findSubmissionScopeByIdAndJudgeId(
            @Param("assignmentId") Long assignmentId,
            @Param("judgeId") Long judgeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from ReviewAssignment assignment
            where assignment.id = :assignmentId
              and assignment.contestJudge.id = :judgeId
              and assignment.reviewRoundEntry.id = :entryId
            """)
    Optional<ReviewAssignment> findByIdAndJudgeIdAndEntryIdForUpdate(
            @Param("assignmentId") Long assignmentId,
            @Param("judgeId") Long judgeId,
            @Param("entryId") Long entryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from ReviewAssignment assignment
            where assignment.contestJudge.id = :judgeId
              and assignment.reviewRoundEntry.id in :entryIds
            order by assignment.reviewRoundEntry.id, assignment.id
            """)
    List<ReviewAssignment> findAllForUpdateByJudgeIdAndEntryIdIn(
            @Param("judgeId") Long judgeId,
            @Param("entryIds") Collection<Long> entryIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from ReviewAssignment assignment
            where assignment.id = :assignmentId
              and assignment.contestJudge.id = :judgeId
              and assignment.reviewRoundEntry.reviewRound.id = :reviewRoundId
            """)
    Optional<ReviewAssignment>
            findByIdAndJudgeIdAndReviewRoundIdForUpdate(
                    @Param("assignmentId") Long assignmentId,
                    @Param("judgeId") Long judgeId,
                    @Param("reviewRoundId") Long reviewRoundId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select assignment
            from ReviewAssignment assignment
            where assignment.reviewRoundEntry.id in :entryIds
            order by assignment.reviewRoundEntry.id, assignment.id
            """)
    List<ReviewAssignment>
            findAllForShareByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                    @Param("entryIds") Collection<Long> entryIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from ReviewAssignment assignment
            where assignment.reviewRoundEntry.id in :entryIds
            order by assignment.reviewRoundEntry.id, assignment.id
            """)
    List<ReviewAssignment>
            findAllForUpdateByReviewRoundEntryIdInOrderByEntryIdAscIdAsc(
                    @Param("entryIds") Collection<Long> entryIds);

    @Query("""
            select assignment
            from ReviewAssignment assignment
            join fetch assignment.contestJudge judge
            join fetch assignment.reviewRoundEntry entry
            join fetch entry.reviewRound reviewRound
            join fetch entry.submission submission
            where judge.id = :judgeId
              and reviewRound.id = :reviewRoundId
            order by entry.id, assignment.id
            """)
    List<ReviewAssignment> findAllWithDetailsByJudgeIdAndReviewRoundId(
            @Param("judgeId") Long judgeId,
            @Param("reviewRoundId") Long reviewRoundId);

    @Query("""
            select assignment
            from ReviewAssignment assignment
            join fetch assignment.contestJudge judge
            join fetch judge.contest
            join fetch assignment.reviewRoundEntry entry
            join fetch entry.reviewRound reviewRound
            join fetch entry.submission submission
            where judge.id = :judgeId
            order by reviewRound.roundNo, entry.id, assignment.id
            """)
    List<ReviewAssignment> findAllWithDetailsByJudgeId(
            @Param("judgeId") Long judgeId);

    // 심사위원 포털 목록 — 라운드, 제출물, 팀을 한 번에 적재해 N+1 조회를 막는다.
    @Query("""
            select ra
            from ReviewAssignment ra
            join fetch ra.reviewRoundEntry e
            join fetch e.reviewRound
            join fetch e.submission s
            join fetch s.team
            where ra.contestJudge.id = :contestJudgeId
            order by ra.assignedAt asc
            """)
    List<ReviewAssignment> findAllByContestJudgeIdOrderByAssignedAtAsc(
            @Param("contestJudgeId") Long contestJudgeId);

    List<ReviewAssignment> findAllByReviewRoundEntryIdIn(
            Collection<Long> entryIds);

    boolean existsByContestJudgeId(Long contestJudgeId);

    // Current read after the judge lock: a repeatable-read snapshot may predate
    // an assignment committed while waiting for that lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment from ReviewAssignment assignment
            where assignment.contestJudge.id = :judgeId
            order by assignment.id
            """)
    List<ReviewAssignment> findAllForUpdateByContestJudgeId(@Param("judgeId") Long judgeId);

    @Query("""
            select assignment
            from ReviewAssignment assignment
            join fetch assignment.reviewRoundEntry entry
            join fetch entry.reviewRound
            where assignment.contestJudge.id = :judgeId
              and entry.submission.id = :submissionId
            order by assignment.id
            """)
    List<ReviewAssignment> findAllWithRoundByJudgeIdAndSubmissionId(
            @Param("judgeId") Long judgeId,
            @Param("submissionId") Long submissionId);

    @Query("""
            select judge.id as judgeId,
                   judge.name as judgeName,
                   coalesce(sum(case
                       when (:reviewRoundId is null
                             or reviewRound.id = :reviewRoundId)
                            and assignment.status in (
                                'ASSIGNED',
                                'COMPLETED'
                            )
                       then 1 else 0 end), 0) as assignedCount,
                   coalesce(sum(case
                       when (:reviewRoundId is null
                             or reviewRound.id = :reviewRoundId)
                            and assignment.status = 'COMPLETED'
                       then 1 else 0 end), 0) as completedCount,
                   coalesce(sum(case
                       when (:reviewRoundId is null
                             or reviewRound.id = :reviewRoundId)
                            and assignment.status = 'ASSIGNED'
                            and (assignment.dueAt is null
                                 or assignment.dueAt > :now)
                       then 1 else 0 end), 0) as pendingCount,
                   coalesce(sum(case
                       when (:reviewRoundId is null
                             or reviewRound.id = :reviewRoundId)
                            and assignment.status = 'ASSIGNED'
                            and assignment.dueAt is not null
                            and assignment.dueAt <= :now
                       then 1 else 0 end), 0) as overdueCount
            from ContestJudge judge
            left join ReviewAssignment assignment
              on assignment.contestJudge = judge
            left join assignment.reviewRoundEntry entry
            left join entry.reviewRound reviewRound
            where judge.contest.id = :contestId
            group by judge.id, judge.name, judge.createdAt
            order by judge.createdAt, judge.id
            """)
    List<JudgeProgressProjection> findJudgeProgressByContestId(
            @Param("contestId") Long contestId,
            @Param("reviewRoundId") Long reviewRoundId,
            @Param("now") java.time.LocalDateTime now);
}
