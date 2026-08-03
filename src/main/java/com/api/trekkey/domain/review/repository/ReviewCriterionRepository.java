package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewCriterion;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewCriterionRepository extends JpaRepository<ReviewCriterion, Long> {

    List<ReviewCriterion> findAllByReviewRoundIdInOrderBySortOrderAsc(
            Collection<Long> reviewRoundIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select criterion
            from ReviewCriterion criterion
            where criterion.reviewRound.id in :reviewRoundIds
            order by criterion.reviewRound.id, criterion.sortOrder, criterion.id
            """)
    List<ReviewCriterion> findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
            @Param("reviewRoundIds") Collection<Long> reviewRoundIds);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select criterion
            from ReviewCriterion criterion
            where criterion.reviewRound.id = :reviewRoundId
            order by criterion.sortOrder, criterion.id
            """)
    List<ReviewCriterion> findAllForShareByReviewRoundIdOrderBySortOrderAsc(
            @Param("reviewRoundId") Long reviewRoundId);

    void deleteByReviewRoundIdIn(Collection<Long> reviewRoundIds);
}
