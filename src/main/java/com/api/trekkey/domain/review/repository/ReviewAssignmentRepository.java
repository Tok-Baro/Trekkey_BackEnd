package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ReviewAssignment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewAssignmentRepository extends JpaRepository<ReviewAssignment, Long> {

    // 심사위원 포털 목록 — entry→submission→team 체인을 한 번에 적재 (N+1 방지)
    @Query("""
            select ra
            from ReviewAssignment ra
            join fetch ra.contestStageEntry e
            join fetch e.submission s
            join fetch s.team
            where ra.contestJudge.id = :contestJudgeId
            order by ra.assignedAt asc
            """)
    List<ReviewAssignment> findAllByContestJudgeIdOrderByAssignedAtAsc(@Param("contestJudgeId") Long contestJudgeId);

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
