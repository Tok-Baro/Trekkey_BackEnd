package com.api.trekkey.domain.submission.publicapi.web.dto;

import com.api.trekkey.domain.submission.entity.IntegrityStatus;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import java.time.LocalDateTime;
import java.util.List;

public record SubmissionRes(
        // 공개 식별자 — 내부 PK는 노출하지 않는다
        String id,
        String teamId,
        String teamName,
        String title,
        SubmissionStatus status,
        long sourceVersion,
        IntegrityStatus integrityStatus,
        LocalDateTime finalizedAt,
        LocalDateTime submittedAt,
        List<SubmissionFileRes> files
) {
    public static SubmissionRes from(Submission submission, List<SubmissionFileRes> files) {
        return new SubmissionRes(
                submission.getPublicId(),
                submission.getTeam().getPublicId(),
                submission.getTeam().getName(),
                submission.getTitle(),
                submission.getStatus(),
                submission.getSourceVersion(),
                submission.getIntegrityStatus(),
                submission.getFinalizedAt(),
                submission.getSubmittedAt(),
                files
        );
    }
}
