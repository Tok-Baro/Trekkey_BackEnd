package com.api.template.global.response.code;

public interface BaseResponseCode {
    String getCode();

    int getHttpStatus();

    String getMessage();
}
