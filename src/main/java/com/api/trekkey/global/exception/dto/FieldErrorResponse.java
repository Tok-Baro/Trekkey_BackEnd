package com.api.trekkey.global.exception.dto;

public record FieldErrorResponse(
        String field,
        String rejectedValue,
        String message
) {
}
