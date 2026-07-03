package com.api.trekkey.global.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BaseException extends RuntimeException {

    private final BaseResponseCode baseResponseCode;
}
