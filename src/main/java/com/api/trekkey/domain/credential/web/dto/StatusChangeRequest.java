package com.api.trekkey.domain.credential.web.dto;

import com.api.trekkey.domain.credential.service.dto.StatusChangeCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(
        @NotBlank(message = "action은 필수입니다.")
        @Pattern(regexp = "REVOKE|SUPERSEDE", message = "action은 REVOKE 또는 SUPERSEDE여야 합니다.")
        String action,
        @Size(max = 128, message = "replacementCredentialPublicId는 128자 이하여야 합니다.")
        String replacementCredentialPublicId,
        @Min(value = 1, message = "issuerKeyVersion은 1 이상이어야 합니다.")
        int issuerKeyVersion,
        @NotBlank(message = "reasonCode는 필수입니다.")
        @Pattern(regexp = "[A-Z0-9_]{1,64}", message = "reasonCode는 영문 대문자, 숫자, 밑줄만 사용할 수 있습니다.")
        String reasonCode,
        @Size(max = 2000, message = "reasonDetail은 2000자 이하여야 합니다.")
        String reasonDetail) {

    public StatusChangeCommand toCommand() {
        return new StatusChangeCommand(
                StatusChangeCommand.Action.valueOf(action),
                replacementCredentialPublicId,
                issuerKeyVersion,
                reasonCode,
                reasonDetail);
    }
}
