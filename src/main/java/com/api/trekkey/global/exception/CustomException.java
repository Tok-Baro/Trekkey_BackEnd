package com.api.trekkey.global.exception;

import com.api.trekkey.global.response.code.BaseResponseCode;

public class CustomException extends BaseException {

    public CustomException(BaseResponseCode baseResponseCode) {
        super(baseResponseCode);
    }
}
