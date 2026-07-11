package com.api.trekkey.domain.auth.web.controller;

import com.api.trekkey.domain.auth.service.AuthService;
import com.api.trekkey.domain.auth.support.RefreshTokenCookieProvider;
import com.api.trekkey.domain.auth.web.dto.AuthResult;
import com.api.trekkey.domain.auth.web.dto.UserSignInReq;
import com.api.trekkey.domain.auth.web.dto.UserSignInRes;
import com.api.trekkey.domain.user.web.dto.UserSignUpReq;
import com.api.trekkey.global.response.SuccessResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<?>> signUp(
            @RequestBody @Valid UserSignUpReq userSignUpReq){
        authService.signUp(userSignUpReq);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.createSuccess("회원가입성공"));
    }

    @PostMapping("/signin")
    public ResponseEntity<SuccessResponse<UserSignInRes>> signIn(
            @RequestBody @Valid UserSignInReq userSignInReq) {
        AuthResult authResult = authService.signIn(userSignInReq);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.createCookie(authResult.refreshToken()).toString())
                .body(SuccessResponse.ok(authResult.userSignInRes()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<UserSignInRes>> refresh(HttpServletRequest request) {
        String refreshToken = refreshTokenCookieProvider.extract(request);
        AuthResult authResult = authService.reissue(refreshToken);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.createCookie(authResult.refreshToken()).toString())
                .body(SuccessResponse.ok(authResult.userSignInRes()));
    }

    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse<?>> logout(HttpServletRequest request) {
        String refreshToken = refreshTokenCookieProvider.extract(request);
        authService.logout(refreshToken);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.deleteCookie().toString())
                .body(SuccessResponse.emptyCustom("로그아웃 성공"));
    }
}
