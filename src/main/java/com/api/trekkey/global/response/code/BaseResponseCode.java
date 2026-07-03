package com.api.trekkey.global.response.code;

public interface BaseResponseCode {
    String getCode();

    int getHttpStatus();

    String getMessage();
}
