package com.api.trekkey.domain.auth.service;

import com.api.trekkey.domain.auth.web.dto.AuthResult;
import com.api.trekkey.domain.auth.web.dto.UserSignInReq;
import com.api.trekkey.domain.invitation.web.dto.request.AdminSignUpReq;
import com.api.trekkey.domain.user.web.dto.UserSignUpReq;

public interface AuthService {

    // 회원가입 요청을 검증하고, 비밀번호를 암호화해서 새 사용자를 저장한다.
    void signUp(UserSignUpReq userSignUpReq);

    // 초대 토큰을 검증하고 승인 대기(PENDING_APPROVAL) 상태의 관리자 계정을 생성한다. (설계 §2-3 게이트 2)
    void signUpAdmin(AdminSignUpReq adminSignUpReq);

    // 이메일/비밀번호가 맞으면 access token은 응답 body로, refresh token은 쿠키로 내려보낼 수 있게 묶어서 반환한다.
    AuthResult signIn(UserSignInReq userSignInReq);

    // refresh token이 유효하면 기존 refresh token을 폐기하고 새 access/refresh token 쌍을 발급한다.
    AuthResult reissue(String refreshToken);

    // 전달된 access token을 Redis에서 차단하고 refresh token family를 DB에서 폐기한다.
    void logout(String accessToken, String refreshToken);
}
