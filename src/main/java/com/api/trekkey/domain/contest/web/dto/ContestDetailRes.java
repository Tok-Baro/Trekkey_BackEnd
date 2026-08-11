package com.api.trekkey.domain.contest.web.dto;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageType;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ContestDetailRes(
        // 공개 식별자 — 내부 PK는 노출하지 않는다
        String id,
        String title,
        String department,
        ContestStatus status,
        ParticipationType participationType,
        int maxTeamMembers,
        int awardCount,
        String posterUrl,
        String summary,
        String target,
        String applicationMethod,
        String benefits,
        String tags,
        String detailHtml,
        long viewCount,
        // 프론트 표시용 유도 필드: 신청 단계 시작/종료, 제출 단계 마감
        LocalDateTime applicationStartsAt,
        LocalDateTime applicationEndsAt,
        LocalDateTime submissionDueAt,
        List<StageRes> stages
) {
    public static ContestDetailRes of(Contest contest, List<StageRes> stages) {
        StageRes applicationStage = stages.stream()
                .filter(stage -> stage.stageType() == StageType.APPLICATION)
                .findFirst()
                .orElse(null);
        LocalDateTime submissionDueAt = stages.stream()
                .filter(stage -> stage.stageType() == StageType.SUBMISSION)
                .map(StageRes::endsAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new ContestDetailRes(
                contest.getPublicId(),
                contest.getTitle(),
                contest.getDepartment(),
                contest.getStatus(),
                contest.getParticipationType(),
                contest.getMaxTeamMembers(),
                contest.getAwardCount(),
                contest.getPosterUrl(),
                contest.getSummary(),
                contest.getTarget(),
                contest.getApplicationMethod(),
                contest.getBenefits(),
                contest.getTags(),
                contest.getDetailHtml(),
                contest.getViewCount(),
                applicationStage == null ? null : applicationStage.startsAt(),
                applicationStage == null ? null : applicationStage.endsAt(),
                submissionDueAt,
                stages
        );
    }
}
