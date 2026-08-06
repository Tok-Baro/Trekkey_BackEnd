package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewScoreItemRepository
        extends JpaRepository<ReviewScoreItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select item
            from ReviewScoreItem item
            where item.review.id = :reviewId
            order by item.reviewCriterion.id, item.id
            """)
    List<ReviewScoreItem> findAllForShareByReviewIdOrderByCriterionIdAsc(
            @Param("reviewId") Long reviewId);

    List<ReviewScoreItem> findAllByReviewIdIn(Collection<Long> reviewIds);

    @Query("""
            select item
            from ReviewScoreItem item
            join fetch item.review review
            join fetch item.reviewCriterion criterion
            where review.id in :reviewIds
            order by review.id asc, criterion.sortOrder asc,
                     criterion.id asc, item.id asc
            """)
    List<ReviewScoreItem> findAllWithCriterionByReviewIdIn(
            @Param("reviewIds") Collection<Long> reviewIds);
}
