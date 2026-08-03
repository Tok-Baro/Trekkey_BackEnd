package com.api.trekkey.domain.contest.repository;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StageType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContestStageRepository extends JpaRepository<ContestStage, Long> {

    List<ContestStage> findAllByContestIdInAndStageTypeOrderBySequenceNoAsc(
            Collection<Long> contestIds,
            StageType stageType);

    List<ContestStage> findAllByContestIdAndStageTypeInOrderBySequenceNoAsc(
            Long contestId,
            Collection<StageType> stageTypes);

    List<ContestStage> findAllByContestIdOrderBySequenceNoAsc(Long contestId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select stage
            from ContestStage stage
            where stage.contest.id = :contestId
              and stage.stageType = :stageType
            order by stage.sequenceNo
            """)
    List<ContestStage> findAllForShareByContestIdAndStageTypeOrderBySequenceNoAsc(
            @Param("contestId") Long contestId,
            @Param("stageType") StageType stageType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select stage
            from ContestStage stage
            where stage.contest.id = :contestId
            order by stage.sequenceNo
            """)
    List<ContestStage> findAllForUpdateByContestIdOrderBySequenceNoAsc(
            @Param("contestId") Long contestId);

    @Query("""
            select stage.contest.organization.id
            from ContestStage stage
            where stage.id = :id
            """)
    Optional<Long> findOrganizationIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select stage from ContestStage stage where stage.id = :id")
    Optional<ContestStage> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select stage from ContestStage stage where stage.id = :id")
    Optional<ContestStage> findByIdForShare(@Param("id") Long id);
}
