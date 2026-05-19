package com.api.template.global.exception.dto;

public record FieldErrorResponse(
        String field,
        String rejectedValue,
        String message
) {
}
