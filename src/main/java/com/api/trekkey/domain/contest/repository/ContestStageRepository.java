package com.api.trekkey.domain.contest.repository;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StageType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestStageRepository extends JpaRepository<ContestStage, Long> {

    List<ContestStage> findAllByContestIdInAndStageTypeOrderBySequenceNoAsc(
            Collection<Long> contestIds,
            StageType stageType);

    List<ContestStage> findAllByContestIdAndStageTypeInOrderBySequenceNoAsc(
            Long contestId,
            Collection<StageType> stageTypes);
}
