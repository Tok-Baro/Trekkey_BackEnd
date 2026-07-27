package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRoundEntryRepository
        extends JpaRepository<ReviewRoundEntry, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select entry
            from ReviewRoundEntry entry
            where entry.reviewStage.id = :reviewStageId
            order by entry.id
            """)
    List<ReviewRoundEntry> findAllForShareByReviewStageIdOrderByIdAsc(
            @Param("reviewStageId") Long reviewStageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entry
            from ReviewRoundEntry entry
            where entry.reviewStage.id = :reviewStageId
            order by entry.id
            """)
    List<ReviewRoundEntry> findAllForUpdateByReviewStageIdOrderByIdAsc(
            @Param("reviewStageId") Long reviewStageId);

    @Query("""
            select entry
            from ReviewRoundEntry entry
            join fetch entry.reviewStage
            join fetch entry.submission submission
            join fetch submission.team
            where entry.reviewStage.id = :reviewStageId
            order by entry.createdAt, entry.id
            """)
    List<ReviewRoundEntry> findAllByReviewStageIdOrderByCreatedAtAscIdAsc(
            @Param("reviewStageId") Long reviewStageId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select entry
            from ReviewRoundEntry entry
            join fetch entry.reviewStage
            join fetch entry.submission submission
            join fetch submission.team
            where entry.reviewStage.id = :reviewStageId
            order by entry.createdAt, entry.id
            """)
    List<ReviewRoundEntry>
            findAllForShareByReviewStageIdOrderByCreatedAtAscIdAsc(
                    @Param("reviewStageId") Long reviewStageId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select stage
            from ContestStage stage
            where stage.id in :reviewStageIds
              and exists (
                    select entry.id
                    from ReviewRoundEntry entry
                    where entry.reviewStage = stage
              )
            """)
    List<ContestStage> findStagesWithEntriesForShare(
            @Param("reviewStageIds") Collection<Long> reviewStageIds);
}
