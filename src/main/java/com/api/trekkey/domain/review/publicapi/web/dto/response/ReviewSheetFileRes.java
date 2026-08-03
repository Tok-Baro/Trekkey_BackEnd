package com.api.trekkey.domain.review.publicapi.web.dto.response;

import com.api.trekkey.domain.submission.entity.SubmissionFile;

public record ReviewSheetFileRes(
        Long fileId,
        String originalName,
        String contentType,
        long sizeBytes
) {

    public static ReviewSheetFileRes from(SubmissionFile file) {
        return new ReviewSheetFileRes(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes()
        );
    }
}
