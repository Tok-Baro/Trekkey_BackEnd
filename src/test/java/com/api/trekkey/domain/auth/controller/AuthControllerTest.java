package com.api.trekkey.domain.auth.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.auth.service.AuthService;
import com.api.trekkey.domain.auth.support.RefreshTokenCookieProvider;
import com.api.trekkey.domain.auth.web.dto.AuthResult;
import com.api.trekkey.domain.auth.web.dto.UserSessionRes;
import com.api.trekkey.domain.auth.web.dto.UserSignInRes;
import com.api.trekkey.domain.auth.web.controller.AuthController;
import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.web.dto.UserSignUpReq;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, refreshTokenCookieProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("유효한 회원가입 요청은 201과 성공 메시지를 반환한다")
    void signUp_returnsCreatedResponse() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignUpRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_201"))
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.message").value("회원가입성공"));

        ArgumentCaptor<UserSignUpReq> requestCaptor = ArgumentCaptor.forClass(UserSignUpReq.class);
        verify(authService).signUp(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().getOrganizationId()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().getEmail()).isEqualTo("hong@example.com");
    }

    @Test
    @DisplayName("중복 이메일 예외는 공통 오류 응답으로 반환한다")
    void signUp_returnsConflictWhenEmailAlreadyExists() throws Exception {
        willThrow(new CustomException(UserErrorResponseCode.USER_EXISTS_EMAIL))
                .given(authService)
                .signUp(any(UserSignUpReq.class));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignUpRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("USER_EXISTS_EMAIL"))
                .andExpect(jsonPath("$.httpStatus").value(409))
                .andExpect(jsonPath("$.message").value("이미 해당 이메일이 존재합니다"));
    }

    @Test
    @DisplayName("가입할 수 없는 학교 예외는 공통 오류 응답으로 반환한다")
    void signUp_returnsNotFoundWhenOrganizationIsNotActive() throws Exception {
        willThrow(new CustomException(OrganizationErrorResponseCode.ORGANIZATION_NOT_FOUND))
                .given(authService)
                .signUp(any(UserSignUpReq.class));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignUpRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
                .andExpect(jsonPath("$.httpStatus").value(404))
                .andExpect(jsonPath("$.message").value("가입 가능한 학교를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("필수값 또는 이메일 형식이 유효하지 않으면 400 검증 오류를 반환한다")
    void signUp_returnsBadRequestWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "email": "not-an-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.message").value("요청 값 검증에 실패했습니다."))
                .andExpect(jsonPath("$.data[*].field", hasItems("organizationId", "name", "email", "password")));
    }

    @Test
    @DisplayName("로그인에 성공하면 access token과 사용자 세션, HttpOnly refresh cookie를 반환한다")
    void signIn_returnsAccessTokenSessionAndRefreshCookie() throws Exception {
        given(authService.signIn(any())).willReturn(authResult("access-token", "refresh-token"));
        given(refreshTokenCookieProvider.createCookie("refresh-token"))
                .willReturn(refreshCookie("refresh-token", 1_209_600));

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignInRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.userSessionRes.id").value(1L))
                .andExpect(jsonPath("$.data.userSessionRes.email").value("hong@example.com"))
                .andExpect(jsonPath("$.data.userSessionRes.role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")));
    }

    @Test
    @DisplayName("로그인 자격이 틀리면 401을 반환하고 cookie를 설정하지 않는다")
    void signIn_returnsUnauthorizedWithoutCookieWhenCredentialsAreInvalid() throws Exception {
        given(authService.signIn(any()))
                .willThrow(new CustomException(UserErrorResponseCode.USER_INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignInRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("로그인 이메일과 비밀번호 형식이 잘못되면 400을 반환한다")
    void signIn_returnsBadRequestWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.data[*].field", hasItems("email", "password")));
    }

    @Test
    @DisplayName("refresh cookie가 유효하면 새 access token과 rotation된 cookie를 반환한다")
    void refresh_returnsNewAccessTokenAndRefreshCookie() throws Exception {
        given(refreshTokenCookieProvider.extract(any())).willReturn("old-refresh");
        given(authService.reissue("old-refresh")).willReturn(authResult("new-access", "new-refresh"));
        given(refreshTokenCookieProvider.createCookie("new-refresh"))
                .willReturn(refreshCookie("new-refresh", 1_209_600));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new-refresh")));

        verify(authService).reissue("old-refresh");
    }

    @Test
    @DisplayName("로그아웃하면 DB token을 폐기하고 refresh cookie를 삭제한다")
    void logout_revokesTokenAndDeletesRefreshCookie() throws Exception {
        given(refreshTokenCookieProvider.extract(any())).willReturn("refresh-token");
        given(refreshTokenCookieProvider.deleteCookie()).willReturn(refreshCookie("", 0));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃 성공"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).logout("refresh-token");
    }

    private String validSignUpRequest() {
        return """
                {
                  "organizationId": 1,
                  "name": "홍길동",
                  "email": "hong@example.com",
                  "password": "password123",
                  "studentId": "20240001",
                  "major": "컴퓨터공학부"
                }
                """;
    }

    private String validSignInRequest() {
        return """
                {
                  "email": "hong@example.com",
                  "password": "password123"
                }
                """;
    }

    private AuthResult authResult(String accessToken, String refreshToken) {
        UserSessionRes session = new UserSessionRes(
                1L,
                "홍길동",
                "hong@example.com",
                UserRole.PARTICIPANT,
                "20240001",
                "컴퓨터공학부"
        );
        return new AuthResult(new UserSignInRes(accessToken, session), refreshToken);
    }

    private ResponseCookie refreshCookie(String value, long maxAge) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
