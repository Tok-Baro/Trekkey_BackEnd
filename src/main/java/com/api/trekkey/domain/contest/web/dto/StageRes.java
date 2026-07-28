package com.api.trekkey.domain.contest.web.dto;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StagePassRule;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageTargetType;
import com.api.trekkey.domain.contest.entity.StageType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StageRes(
        Long id,
        String name,
        StageType stageType,
        int sequenceNo,
        StageStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        StageTargetType targetType,
        StagePassRule passRule,
        Integer passCount,
        BigDecimal minScore,
        List<CriterionRes> criteria
) {
    public static StageRes from(ContestStage stage, List<CriterionRes> criteria) {
        return new StageRes(
                stage.getId(),
                stage.getName(),
                stage.getStageType(),
                stage.getSequenceNo(),
                stage.getStatus(),
                stage.getStartsAt(),
                stage.getEndsAt(),
                stage.getTargetType(),
                stage.getPassRule(),
                stage.getPassCount(),
                stage.getMinScore(),
                criteria
        );
    }
}
