package com.api.trekkey.domain.auth.service;

import com.api.trekkey.domain.user.web.dto.UserSignUpReq;

public interface AuthService {
    void signUp(UserSignUpReq userSignUpReq);
}
