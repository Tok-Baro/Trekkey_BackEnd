package com.api.template.global.response;

import com.api.template.global.response.code.BaseResponseCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public class BaseResponse {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Boolean isSuccess;
    private final String code;
    private final String message;
    private final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);

    public static BaseResponse of(Boolean isSuccess, BaseResponseCode baseResponseCode) {
        return new BaseResponse(isSuccess, baseResponseCode.getCode(), baseResponseCode.getMessage());
    }

    public static BaseResponse of(Boolean isSuccess, BaseResponseCode baseResponseCode, String message) {
        return new BaseResponse(isSuccess, baseResponseCode.getCode(), message);
    }
}
