package com.api.trekkey.domain.team.publicapi.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

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

        @NotNull(message = "팀원 목록을 입력해주세요.")
        @Size(max = 4, message = "팀원은 대표자를 제외하고 4명 이하여야 합니다.")
        List<
                @NotNull(message = "팀원 식별자는 비어 있을 수 없습니다.")
                @Positive(message = "팀원 식별자는 양수여야 합니다.")
                Long> memberUserIds,

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
