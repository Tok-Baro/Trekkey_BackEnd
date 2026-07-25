package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewScoreItemRepository extends JpaRepository<ReviewScoreItem, Long> {

    List<ReviewScoreItem> findAllByReviewIdIn(Collection<Long> reviewIds);
}
