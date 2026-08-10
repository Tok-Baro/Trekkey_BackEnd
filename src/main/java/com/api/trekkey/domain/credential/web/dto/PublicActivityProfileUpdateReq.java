package com.api.trekkey.domain.credential.web.dto;

import jakarta.validation.constraints.NotNull;

public record PublicActivityProfileUpdateReq(@NotNull Boolean enabled) {
}
