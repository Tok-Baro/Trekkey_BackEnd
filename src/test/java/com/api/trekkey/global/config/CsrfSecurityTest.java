package com.api.trekkey.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.auth.service.AuthService;
import com.api.trekkey.domain.auth.support.RefreshTokenCookieProvider;
import com.api.trekkey.domain.auth.web.controller.AuthController;
import com.api.trekkey.domain.auth.web.controller.CsrfController;
import com.api.trekkey.domain.auth.web.dto.AuthResult;
import com.api.trekkey.domain.auth.web.dto.UserSignInRes;
import com.api.trekkey.global.security.handler.JwtAccessDeniedHandler;
import com.api.trekkey.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.trekkey.global.security.jwt.JwtAuthenticationFilter;
import com.api.trekkey.global.security.jwt.JwtExtractor;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = {AuthController.class, CsrfController.class}, properties = {
        "security.jwt.refresh-cookie-name=refresh",
        "security.jwt.refresh-cookie-secure=true",
        "security.jwt.refresh-cookie-same-site=None",
        "security.jwt.refresh-expiration=604800",
        "app.cors.allowed-origin=https://frontend.example"
})
@Import({
        SecurityConfig.class,
        WebConfig.class,
        JwtAuthenticationFilter.class,
        JwtExtractor.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        RefreshTokenCookieProvider.class
})
class CsrfSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void csrfEndpoint_returnsMaskedTokenAndHttpOnlyCookieWithoutSession() throws Exception {
        CsrfPair pair = issueCsrf();
        assertThat(pair.cookie().isHttpOnly()).isTrue();
        assertThat(pair.cookie().getSecure()).isTrue();
        assertThat(pair.cookie().getPath()).isEqualTo("/api/auth");
        assertThat(pair.cookie().getAttribute("SameSite")).isEqualTo("None");
        assertThat(pair.cookie().getDomain()).isNull();
        assertThat(pair.token()).isNotBlank().isNotEqualTo(pair.cookie().getValue());
        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/signin", "/refresh", "/logout", "/signup", "/signup/admin"})
    void authPost_withoutCsrf_rejectedBeforeService(String path) throws Exception {
        mockMvc.perform(post("/api/auth" + path)
                        .cookie(new Cookie("refresh", "valid-refresh")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/signin", "/refresh", "/logout"})
    void authPost_withCookieButNoHeader_rejected(String path) throws Exception {
        CsrfPair pair = issueCsrf();
        mockMvc.perform(post("/api/auth" + path).cookie(pair.cookie()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/signin", "/refresh", "/logout"})
    void authPost_withMismatchedToken_rejected(String path) throws Exception {
        CsrfPair first = issueCsrf();
        CsrfPair second = issueCsrf();
        mockMvc.perform(post("/api/auth" + path)
                        .cookie(first.cookie())
                        .header("X-XSRF-TOKEN", second.token()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void headerWithoutCookie_rejected() throws Exception {
        CsrfPair pair = issueCsrf();
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-XSRF-TOKEN", pair.token()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void rawCookieValueIsNotAcceptedAsMaskedHeader() throws Exception {
        CsrfPair pair = issueCsrf();
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(pair.cookie())
                        .header("X-XSRF-TOKEN", pair.cookie().getValue()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void issuedTokenCanBeReusedForSignInRefreshAndLogout() throws Exception {
        CsrfPair pair = issueCsrf();
        given(authService.signIn(any())).willReturn(
                new AuthResult(new UserSignInRes("access-token", null), "first-refresh"));
        given(authService.reissue("first-refresh")).willReturn(
                new AuthResult(new UserSignInRes("new-access", null), "next-refresh"));

        MvcResult signIn = mockMvc.perform(post("/api/auth/signin")
                        .cookie(pair.cookie())
                        .header("X-XSRF-TOKEN", pair.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andReturn();

        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(pair.cookie(), signIn.getResponse().getCookie("refresh"))
                        .header("X-XSRF-TOKEN", pair.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andReturn();

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                        .cookie(pair.cookie(), refresh.getResponse().getCookie("refresh"))
                        .header("X-XSRF-TOKEN", pair.token()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(logout.getResponse().getCookie("refresh").getMaxAge()).isZero();
        assertThat(signIn.getRequest().getSession(false)).isNull();
        assertThat(refresh.getRequest().getSession(false)).isNull();
        assertThat(logout.getRequest().getSession(false)).isNull();
        verify(authService).signIn(any());
        verify(authService).reissue("first-refresh");
        verify(authService).logout("next-refresh");
    }

    @Test
    void allowedOrigin_canFetchTokenAndPreflightCsrfHeader() throws Exception {
        mockMvc.perform(get("/api/auth/csrf")
                        .header(HttpHeaders.ORIGIN, "https://frontend.example"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://frontend.example"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://frontend.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "X-XSRF-TOKEN"));
        verifyNoInteractions(authService);
    }

    @Test
    void untrustedOrigin_rejectedEvenWithValidCsrf() throws Exception {
        CsrfPair pair = issueCsrf();
        mockMvc.perform(get("/api/auth/csrf")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(pair.cookie())
                        .header("X-XSRF-TOKEN", pair.token())
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    private CsrfPair issueCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new CsrfPair(cookie, data.get("token").asText());
    }

    private record CsrfPair(Cookie cookie, String token) {
    }
}
