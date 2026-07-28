package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.Review;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
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

    boolean existsByAssignmentId(Long assignmentId);

    List<Review> findAllByAssignmentIdIn(Collection<Long> assignmentIds);

    // 라운드 확정 집계 — 심사 대상별 제출 점수를 조회한다.
    @Query("""
            select r.assignment.reviewRoundEntry.id, r.totalScore
            from Review r
            where r.assignment.reviewRoundEntry.id in :entryIds
            """)
    List<Object[]> findTotalScoresByEntryIds(@Param("entryIds") Collection<Long> entryIds);
}
