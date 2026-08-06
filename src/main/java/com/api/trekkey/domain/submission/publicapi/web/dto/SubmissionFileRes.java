package com.api.trekkey.domain.submission.publicapi.web.dto;

import com.api.trekkey.domain.submission.entity.SubmissionFile;

public record SubmissionFileRes(
        Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256
) {
    public static SubmissionFileRes from(SubmissionFile file) {
        return new SubmissionFileRes(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getSha256()
        );
    }
}
