package com.api.trekkey.domain.graduation.web.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.EvaluationStatus;
import com.api.trekkey.domain.graduation.service.GraduationEvaluationService;
import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationRes;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class GraduationEvaluationControllerTest {
    @Mock private GraduationEvaluationService evaluationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthPrincipal principal = AuthPrincipal.of(10L, "student@hansung.ac.kr", List.of("PARTICIPANT"));
        mockMvc = MockMvcBuilders.standaloneSetup(new GraduationEvaluationController(evaluationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver(principal))
                .build();
    }

    @Test
    void frontendStylePostCreatesGraduationEvaluation() throws Exception {
        LocalDate asOf = LocalDate.of(2026, 8, 11);
        given(evaluationService.evaluate(eq(10L), eq(asOf))).willReturn(new GraduationEvaluationRes(
                "evaluation-public-id", EvaluationStatus.ELIGIBLE, asOf,
                LocalDateTime.of(2026, 8, 11, 16, 30),
                new GraduationEvaluationRes.Summary(15, 0, 0),
                List.of(new GraduationEvaluationRes.AppliedPolicy("policy-id", "HANSUNG-ALL", 1, "한성대 통합 정책")),
                List.of(), "자가점검 결과입니다."));

        mockMvc.perform(post("/api/me/graduation/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyAsOf":"2026-08-11"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.evaluationPublicId").value("evaluation-public-id"))
                .andExpect(jsonPath("$.data.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.summary.satisfied").value(15));
    }

    private HandlerMethodArgumentResolver authPrincipalResolver(AuthPrincipal principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return AuthPrincipal.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }
}
