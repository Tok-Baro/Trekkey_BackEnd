package com.api.template.global.response;

import com.api.template.global.response.code.BaseResponseCode;
import com.api.template.global.response.code.SuccessResponseCode;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@JsonPropertyOrder({"isSuccess", "timestamp", "code", "httpStatus", "message", "data"})
public class SuccessResponse<T> extends BaseResponse {

    private final int httpStatus;

    @Schema(description = "응답 데이터", nullable = true)
    private final T data;

    private SuccessResponse(T data, BaseResponseCode baseResponseCode) {
        super(true, baseResponseCode.getCode(), baseResponseCode.getMessage());
        this.httpStatus = baseResponseCode.getHttpStatus();
        this.data = data;
    }

    private SuccessResponse(T data, BaseResponseCode baseResponseCode, String message) {
        super(true, baseResponseCode.getCode(), message);
        this.httpStatus = baseResponseCode.getHttpStatus();
        this.data = data;
    }

    public static SuccessResponse<?> empty() {
        return new SuccessResponse<>(null, SuccessResponseCode.SUCCESS_OK);
    }

    public static SuccessResponse<?> emptyCustom(String message) {
        return new SuccessResponse<>(null, SuccessResponseCode.SUCCESS_OK, message);
    }

    public static <T> SuccessResponse<T> ok(T data) {
        return new SuccessResponse<>(data, SuccessResponseCode.SUCCESS_OK);
    }

    public static <T> SuccessResponse<T> okCustom(T data, String message) {
        return new SuccessResponse<>(data, SuccessResponseCode.SUCCESS_OK, message);
    }

    public static <T> SuccessResponse<T> create(T data) {
        return new SuccessResponse<>(data, SuccessResponseCode.SUCCESS_CREATED);
    }

    public static SuccessResponse<?> createSuccess(String message) {
        return new SuccessResponse<>(null, SuccessResponseCode.SUCCESS_CREATED, message);
    }

    public static SuccessResponse<?> from(BaseResponseCode baseResponseCode) {
        return new SuccessResponse<>(null, baseResponseCode);
    }

    public static <T> SuccessResponse<T> of(T data, BaseResponseCode baseResponseCode) {
        return new SuccessResponse<>(data, baseResponseCode);
    }

    public static <T> SuccessResponse<T> of(T data, BaseResponseCode baseResponseCode, String message) {
        return new SuccessResponse<>(data, baseResponseCode, message);
    }
}
