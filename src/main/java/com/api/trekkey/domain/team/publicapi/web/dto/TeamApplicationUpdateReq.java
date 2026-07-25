package com.api.trekkey.domain.team.publicapi.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeamApplicationUpdateReq(
        @NotBlank(message = "팀명을 입력해주세요.")
        @Size(max = 100, message = "팀명은 100자 이하로 입력해주세요.")
        String teamName,

        @NotBlank(message = "대표자명을 입력해주세요.")
        @Size(max = 100, message = "대표자명은 100자 이하로 입력해주세요.")
        String leaderName,

        @NotBlank(message = "소속을 입력해주세요.")
        @Size(max = 100, message = "소속은 100자 이하로 입력해주세요.")
        String major,

        @NotNull(message = "참가 인원을 입력해주세요.")
        @Min(value = 1, message = "참가 인원은 1명 이상이어야 합니다.")
        @Max(value = 5, message = "참가 인원은 5명 이하여야 합니다.")
        Integer memberCount,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하로 입력해주세요.")
        String contactEmail,

        @NotBlank(message = "연락처를 입력해주세요.")
        @Size(max = 30, message = "연락처는 30자 이하로 입력해주세요.")
        String phone,

        @NotBlank(message = "지원 동기를 입력해주세요.")
        String motivation
) {
}
