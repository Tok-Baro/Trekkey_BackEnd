package com.api.trekkey.domain.contest.admin.web.dto;

import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ContestCreateReq(
        @NotBlank(message = "대회명은 반드시 입력해야합니다")
        @Size(max = 150, message = "대회명은 150자 이하여야 합니다")
        String title,

        @NotBlank(message = "주관 부서는 반드시 입력해야합니다")
        @Size(max = 100, message = "주관 부서는 100자 이하여야 합니다")
        String department,

        @NotNull(message = "대회 상태는 반드시 선택해야합니다")
        ContestStatus status,

        @NotNull(message = "참가 방식은 반드시 선택해야합니다")
        ParticipationType participationType,

        @NotNull(message = "시상 수는 반드시 입력해야합니다")
        @Min(value = 0, message = "시상 수는 0 이상이어야 합니다")
        Integer awardCount,

        @Size(max = 500, message = "포스터 URL은 500자 이하여야 합니다")
        String posterUrl,

        @NotBlank(message = "한 줄 소개는 반드시 입력해야합니다")
        @Size(max = 300, message = "한 줄 소개는 300자 이하여야 합니다")
        String summary,

        @NotBlank(message = "참가 대상은 반드시 입력해야합니다")
        @Size(max = 300, message = "참가 대상은 300자 이하여야 합니다")
        String target,

        @NotBlank(message = "접수 방법은 반드시 입력해야합니다")
        @Size(max = 500, message = "접수 방법은 500자 이하여야 합니다")
        String applicationMethod,

        @NotBlank(message = "시상 및 혜택은 반드시 입력해야합니다")
        @Size(max = 500, message = "시상 및 혜택은 500자 이하여야 합니다")
        String benefits,

        @Size(max = 500, message = "태그는 500자 이하여야 합니다")
        String tags,

        @NotBlank(message = "상세 본문은 반드시 입력해야합니다")
        String detailHtml,

        @NotEmpty(message = "대회에는 최소 1개의 단계가 필요합니다")
        @Valid
        List<StageReq> stages
) {
}
