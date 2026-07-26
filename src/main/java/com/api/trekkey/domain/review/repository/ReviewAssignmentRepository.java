package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewAssignment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewAssignmentRepository extends JpaRepository<ReviewAssignment, Long> {

    List<ReviewAssignment> findAllByContestJudgeIdOrderByAssignedAtAsc(Long contestJudgeId);

    List<ReviewAssignment> findAllByContestStageEntryIdIn(Collection<Long> entryIds);

    boolean existsByContestJudgeId(Long contestJudgeId);

    // 심사위원별 배정/완료 수 집계 — 관리자 심사 현황
    @Query("""
            select ra.contestJudge.id, count(ra), sum(case when ra.status = 'COMPLETED' then 1 else 0 end)
            from ReviewAssignment ra
            where ra.contestJudge.id in :judgeIds
            group by ra.contestJudge.id
            """)
    List<Object[]> countByJudgeIds(@Param("judgeIds") Collection<Long> judgeIds);
}
