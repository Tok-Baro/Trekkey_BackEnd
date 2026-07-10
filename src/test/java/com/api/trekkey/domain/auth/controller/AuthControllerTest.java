package com.api.trekkey.domain.auth.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.auth.service.AuthService;
import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
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
}
