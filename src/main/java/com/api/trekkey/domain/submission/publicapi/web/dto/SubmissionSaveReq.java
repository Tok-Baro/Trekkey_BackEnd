package com.api.trekkey.domain.submission.publicapi.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmissionSaveReq(
        @NotBlank(message = "작품명을 입력해주세요.")
        @Size(max = 150, message = "작품명은 150자 이하로 입력해주세요.")
        String title
) {
}
