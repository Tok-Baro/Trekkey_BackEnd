package com.api.trekkey.domain.contest.web.dto;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import java.time.LocalDateTime;

public record ContestAdminSummaryRes(
        // 공개 식별자 — 내부 PK는 노출하지 않는다
        String id,
        String title,
        String department,
        ContestStatus status,
        ParticipationType participationType,
        int awardCount,
        LocalDateTime createdAt
) {
    public static ContestAdminSummaryRes from(Contest contest) {
        return new ContestAdminSummaryRes(
                contest.getPublicId(),
                contest.getTitle(),
                contest.getDepartment(),
                contest.getStatus(),
                contest.getParticipationType(),
                contest.getAwardCount(),
                contest.getCreatedAt()
        );
    }
}
