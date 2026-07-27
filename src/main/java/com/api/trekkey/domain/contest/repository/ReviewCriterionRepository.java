package com.api.trekkey.domain.contest.repository;

import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewCriterionRepository extends JpaRepository<ReviewCriterion, Long> {

    List<ReviewCriterion> findAllByContestStageIdInOrderBySortOrderAsc(Collection<Long> stageIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select criterion
            from ReviewCriterion criterion
            where criterion.contestStage.id in :stageIds
            order by criterion.sortOrder
            """)
    List<ReviewCriterion> findAllForUpdateByContestStageIdInOrderBySortOrderAsc(
            @Param("stageIds") Collection<Long> stageIds);

    void deleteByContestStageIdIn(Collection<Long> stageIds);
}
