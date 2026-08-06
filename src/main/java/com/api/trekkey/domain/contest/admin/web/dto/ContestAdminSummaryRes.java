package com.api.trekkey.domain.contest.admin.web.dto;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageType;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ContestAdminSummaryRes(
        // 공개 식별자 — 내부 PK는 노출하지 않는다
        String id,
        String title,
        String department,
        String ownerName,
        ContestStatus status,
        ParticipationType participationType,
        int awardCount,
        LocalDateTime applicationStartsAt,
        LocalDateTime applicationEndsAt,
        LocalDateTime submissionDueAt,
        long teamCount,
        long submissionCount,
        long judgeCount,
        LocalDateTime createdAt
) {
    public static ContestAdminSummaryRes from(
            Contest contest,
            List<ContestStage> stages,
            long teamCount,
            long submissionCount,
            long judgeCount
    ) {
        ContestStage applicationStage = stages.stream()
                .filter(stage -> stage.getStageType() == StageType.APPLICATION)
                .findFirst()
                .orElse(null);
        LocalDateTime submissionDueAt = stages.stream()
                .filter(stage -> stage.getStageType() == StageType.SUBMISSION)
                .map(ContestStage::getEndsAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new ContestAdminSummaryRes(
                contest.getPublicId(),
                contest.getTitle(),
                contest.getDepartment(),
                contest.getOwnerUser().getName(),
                contest.getStatus(),
                contest.getParticipationType(),
                contest.getAwardCount(),
                applicationStage == null ? null : applicationStage.getStartsAt(),
                applicationStage == null ? null : applicationStage.getEndsAt(),
                submissionDueAt,
                teamCount,
                submissionCount,
                judgeCount,
                contest.getCreatedAt()
        );
    }
}
