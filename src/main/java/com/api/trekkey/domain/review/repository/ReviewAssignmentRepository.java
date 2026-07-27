package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewAssignment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewAssignmentRepository
        extends JpaRepository<ReviewAssignment, Long> {

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
            join fetch entry.reviewStage stage
            join fetch entry.submission submission
            where judge.id = :judgeId
              and stage.id = :reviewStageId
            order by entry.id, assignment.id
            """)
    List<ReviewAssignment> findAllWithDetailsByJudgeIdAndReviewStageId(
            @Param("judgeId") Long judgeId,
            @Param("reviewStageId") Long reviewStageId);

    @Query("""
            select assignment
            from ReviewAssignment assignment
            join fetch assignment.contestJudge judge
            join fetch judge.contest
            join fetch assignment.reviewRoundEntry entry
            join fetch entry.reviewStage stage
            join fetch entry.submission submission
            where judge.id = :judgeId
            order by stage.sequenceNo, entry.id, assignment.id
            """)
    List<ReviewAssignment> findAllWithDetailsByJudgeId(
            @Param("judgeId") Long judgeId);
}
