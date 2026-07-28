package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
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
            where entry.reviewRound.id = :reviewRoundId
            order by entry.id
            """)
    List<ReviewRoundEntry> findAllForShareByReviewRoundIdOrderByIdAsc(
            @Param("reviewRoundId") Long reviewRoundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entry
            from ReviewRoundEntry entry
            where entry.reviewRound.id = :reviewRoundId
            order by entry.id
            """)
    List<ReviewRoundEntry> findAllForUpdateByReviewRoundIdOrderByIdAsc(
            @Param("reviewRoundId") Long reviewRoundId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select entry
            from ReviewRoundEntry entry
            where entry.id = :entryId
              and entry.reviewRound.id = :reviewRoundId
            """)
    Optional<ReviewRoundEntry> findByIdAndReviewRoundIdForShare(
            @Param("entryId") Long entryId,
            @Param("reviewRoundId") Long reviewRoundId);

    @Query("""
            select entry
            from ReviewRoundEntry entry
            join fetch entry.reviewRound
            join fetch entry.submission submission
            join fetch submission.team
            where entry.reviewRound.id = :reviewRoundId
            order by entry.createdAt, entry.id
            """)
    List<ReviewRoundEntry> findAllByReviewRoundIdOrderByCreatedAtAscIdAsc(
            @Param("reviewRoundId") Long reviewRoundId);

    List<ReviewRoundEntry> findAllByReviewRoundIdAndStatus(
            Long reviewRoundId,
            ReviewRoundEntryStatus status);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select entry
            from ReviewRoundEntry entry
            join fetch entry.submission submission
            join fetch submission.team
            where entry.reviewRound.id = :reviewRoundId
              and entry.status = :status
            order by entry.rankNo, entry.id
            """)
    List<ReviewRoundEntry>
            findAllForShareByReviewRoundIdAndStatusOrderByRankNoAscIdAsc(
                    @Param("reviewRoundId") Long reviewRoundId,
                    @Param("status") ReviewRoundEntryStatus status);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select entry
            from ReviewRoundEntry entry
            join fetch entry.reviewRound
            join fetch entry.submission submission
            join fetch submission.team
            where entry.reviewRound.id = :reviewRoundId
            order by entry.createdAt, entry.id
            """)
    List<ReviewRoundEntry>
            findAllForShareByReviewRoundIdOrderByCreatedAtAscIdAsc(
                    @Param("reviewRoundId") Long reviewRoundId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select reviewRound
            from ReviewRound reviewRound
            where reviewRound.id in :reviewRoundIds
              and exists (
                    select entry.id
                    from ReviewRoundEntry entry
                    where entry.reviewRound = reviewRound
              )
            """)
    List<ReviewRound> findRoundsWithEntriesForShare(
            @Param("reviewRoundIds") Collection<Long> reviewRoundIds);
}
