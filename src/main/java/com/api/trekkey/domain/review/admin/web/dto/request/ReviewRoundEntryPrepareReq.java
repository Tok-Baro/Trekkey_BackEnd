package com.api.trekkey.domain.review.admin.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReviewRoundEntryPrepareReq(
        @Size(
                max = 1000,
                message = "수동 심사 대상은 한 번에 1000개까지 선택할 수 있습니다.")
        List<
                @NotBlank(message = "제출물 공개 ID는 비어 있을 수 없습니다.")
                @Size(
                        max = 36,
                        message = "제출물 공개 ID는 36자 이하여야 합니다.")
                String> submissionPublicIds
) {
}
