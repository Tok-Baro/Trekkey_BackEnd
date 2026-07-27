package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.Review;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select review
            from Review review
            where review.assignment.id = :assignmentId
            """)
    Optional<Review> findByAssignmentIdForShare(
            @Param("assignmentId") Long assignmentId);
}
