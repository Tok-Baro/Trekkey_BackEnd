package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewAssignment;
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
}
