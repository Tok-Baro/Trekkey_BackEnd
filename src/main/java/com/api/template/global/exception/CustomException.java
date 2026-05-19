package com.api.template.global.exception;

import com.api.template.global.response.code.BaseResponseCode;

public class CustomException extends BaseException {

    public CustomException(BaseResponseCode baseResponseCode) {
        super(baseResponseCode);
    }
}
