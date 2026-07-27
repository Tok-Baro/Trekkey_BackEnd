package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import jakarta.persistence.LockModeType;
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
}
