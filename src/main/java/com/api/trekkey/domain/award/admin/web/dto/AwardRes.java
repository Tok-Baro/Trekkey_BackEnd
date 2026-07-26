package com.api.trekkey.domain.award.admin.web.dto;

import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AwardRes(
        // 공개 식별자 — 내부 PK는 노출하지 않는다
        String id,
        int awardRankNo,
        String prize,
        String teamName,
        String submissionTitle,
        BigDecimal finalScore,
        AwardStatus status,
        String certificateNo,
        LocalDateTime confirmedAt
) {
    public static AwardRes from(Award award) {
        return new AwardRes(
                award.getPublicId(),
                award.getAwardRankNo(),
                award.getPrize(),
                award.getTeam().getName(),
                award.getContestStageEntry().getSubmission().getTitle(),
                award.getContestStageEntry().getFinalScore(),
                award.getStatus(),
                award.getCertificateNo(),
                award.getConfirmedAt()
        );
    }
}
