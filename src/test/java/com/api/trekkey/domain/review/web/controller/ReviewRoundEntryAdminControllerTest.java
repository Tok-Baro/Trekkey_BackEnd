package com.api.trekkey.domain.review.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.entity.ReviewDecisionType;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.service.ReviewRoundEntryAdminService;
import com.api.trekkey.domain.review.web.dto.response.ReviewRoundEntryRes;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class ReviewRoundEntryAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewRoundEntryAdminService reviewRoundEntryAdminService;

    @BeforeEach
    void setUp() {
        ReviewRoundEntryAdminController controller =
                new ReviewRoundEntryAdminController(reviewRoundEntryAdminService);
        AuthPrincipal principal =
                AuthPrincipal.of(10L, "admin@test.com", List.of("ADMIN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver(principal))
                .build();
    }

    @Test
    @DisplayName("심사 대상 준비에 성공하면 관리자 ID를 전달하고 생성된 대상을 반환한다")
    void prepareEntries_returnsOk() throws Exception {
        given(reviewRoundEntryAdminService.prepareEntries(
                10L,
                "contest-public-id",
                300L
        )).willReturn(List.of(entryRes()));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}/entries/prepare",
                        "contest-public-id",
                        300L
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.message").value("심사 대상을 준비했습니다."))
                .andExpect(jsonPath("$.data[0].id").value(400L))
                .andExpect(jsonPath("$.data[0].reviewRoundId").value(300L))
                .andExpect(jsonPath("$.data[0].submissionPublicId")
                        .value("submission-public-id"))
                .andExpect(jsonPath("$.data[0].submissionTitle").value("AI 작품"))
                .andExpect(jsonPath("$.data[0].teamName").value("트랙키 팀"))
                .andExpect(jsonPath("$.data[0].status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data[0].finalScore").value(91.5))
                .andExpect(jsonPath("$.data[0].decisionType").value("RULE"))
                .andExpect(jsonPath("$.data[0].submissionFinalizedAt[0]")
                        .value(2026))
                .andExpect(jsonPath("$.data[0].submissionFinalizedAt[1]")
                        .value(7))
                .andExpect(jsonPath("$.data[0].submissionFinalizedAt[2]")
                        .value(23));

        verify(reviewRoundEntryAdminService)
                .prepareEntries(10L, "contest-public-id", 300L);
    }

    @Test
    @DisplayName("심사 대상 목록 조회에 성공하면 관리자 ID를 전달하고 목록을 반환한다")
    void getEntries_returnsOk() throws Exception {
        given(reviewRoundEntryAdminService.getEntries(
                10L,
                "contest-public-id",
                300L
        )).willReturn(List.of(entryRes()));

        mockMvc.perform(get(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}/entries",
                        "contest-public-id",
                        300L
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data[0].id").value(400L))
                .andExpect(jsonPath("$.data[0].reviewRoundId").value(300L))
                .andExpect(jsonPath("$.data[0].submissionPublicId")
                        .value("submission-public-id"))
                .andExpect(jsonPath("$.data[0].status").value("ELIGIBLE"));

        verify(reviewRoundEntryAdminService)
                .getEntries(10L, "contest-public-id", 300L);
    }

    @Test
    @DisplayName("준비 중이 아닌 심사 단계에서 대상을 준비하면 409를 반환한다")
    void prepareEntries_returnsConflictWhenPreparationIsNotAllowed() throws Exception {
        given(reviewRoundEntryAdminService.prepareEntries(
                10L,
                "contest-public-id",
                300L
        )).willThrow(new CustomException(
                ReviewErrorResponseCode.REVIEW_ENTRY_PREPARATION_NOT_ALLOWED));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}/entries/prepare",
                        "contest-public-id",
                        300L
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_ENTRY_PREPARATION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.httpStatus").value(409));

        verify(reviewRoundEntryAdminService)
                .prepareEntries(10L, "contest-public-id", 300L);
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

    private ReviewRoundEntryRes entryRes() {
        return new ReviewRoundEntryRes(
                400L,
                300L,
                "submission-public-id",
                "AI 작품",
                "트랙키 팀",
                ReviewRoundEntryStatus.ELIGIBLE,
                new BigDecimal("91.5"),
                1,
                ReviewDecisionType.RULE,
                "상위 점수 선정",
                LocalDateTime.of(2026, 7, 23, 18, 0),
                LocalDateTime.of(2026, 7, 24, 12, 0),
                LocalDateTime.of(2026, 7, 24, 10, 0),
                LocalDateTime.of(2026, 7, 24, 12, 0)
        );
    }
}
