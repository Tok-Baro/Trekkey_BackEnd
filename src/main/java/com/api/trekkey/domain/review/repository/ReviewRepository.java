package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.Review;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByAssignmentId(Long assignmentId);

    List<Review> findAllByAssignmentIdIn(Collection<Long> assignmentIds);

    // 라운드 집계 — entry별 제출된 심사 결과 (erd-mvp §5 라운드 마감)
    @Query("""
            select r.assignment.contestStageEntry.id, r.totalScore
            from Review r
            where r.assignment.contestStageEntry.id in :entryIds
            """)
    List<Object[]> findTotalScoresByEntryIds(@Param("entryIds") Collection<Long> entryIds);
}
