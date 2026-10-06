package com.api.trekkey.domain.auth.web.controller;

import com.api.trekkey.global.response.SuccessResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<SuccessResponse<CsrfTokenRes>> csrf(CsrfToken csrfToken) {
        CsrfTokenRes data = new CsrfTokenRes(csrfToken.getHeaderName(), csrfToken.getToken());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SuccessResponse.ok(data));
    }

    public record CsrfTokenRes(String headerName, String token) {
    }
}
