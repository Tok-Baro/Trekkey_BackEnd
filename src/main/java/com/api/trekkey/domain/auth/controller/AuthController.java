package com.api.trekkey.domain.auth.controller;

import com.api.trekkey.domain.auth.service.AuthService;
import com.api.trekkey.domain.user.web.dto.UserSignUpReq;
import com.api.trekkey.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<?>> signUp(
            @RequestBody @Valid UserSignUpReq userSignUpReq){
        authService.signUp(userSignUpReq);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.createSuccess("회원가입성공"));
    }
}
