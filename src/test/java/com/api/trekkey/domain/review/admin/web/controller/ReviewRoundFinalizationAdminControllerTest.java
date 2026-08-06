package com.api.trekkey.domain.review.admin.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.admin.service.ReviewRoundFinalizationAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundFinalizeReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundEntryRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundFinalizeRes;
import com.api.trekkey.domain.review.entity.ReviewDecisionType;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
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
import org.mockito.ArgumentCaptor;
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
class ReviewRoundFinalizationAdminControllerTest {

    @Mock
    private ReviewRoundFinalizationAdminService finalizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReviewRoundFinalizationAdminController controller =
                new ReviewRoundFinalizationAdminController(
                        finalizationService);
        AuthPrincipal principal =
                AuthPrincipal.of(10L, "admin@test.com", List.of("ADMIN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        authPrincipalResolver(principal))
                .build();
    }

    @Test
    @DisplayName("수동 판정 목록을 포함해 심사 라운드를 확정한다")
    void finalizeRound_returnsFinalizedResults() throws Exception {
        LocalDateTime finalizedAt =
                LocalDateTime.of(2026, 7, 28, 15, 0);
        ReviewRoundEntryRes entry = new ReviewRoundEntryRes(
                100L,
                30L,
                "submission-public-id",
                "AI 작품",
                "트랙키 팀",
                ReviewRoundEntryStatus.SELECTED,
                new BigDecimal("88.50"),
                1,
                ReviewDecisionType.MANUAL,
                10L,
                "사업화 가능성이 높음",
                finalizedAt.minusDays(1),
                finalizedAt,
                finalizedAt.minusDays(2),
                finalizedAt
        );
        given(finalizationService.finalizeRound(
                eq(10L),
                eq("contest-public-id"),
                eq(30L),
                any(ReviewRoundFinalizeReq.class)
        )).willReturn(new ReviewRoundFinalizeRes(
                30L,
                ReviewRoundStatus.FINALIZED,
                finalizedAt,
                List.of(entry)
        ));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}"
                                + "/review-rounds/{roundId}/finalize",
                        "contest-public-id",
                        30L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "manualDecisions": [
                                    {
                                      "entryId": 100,
                                      "status": "SELECTED",
                                      "reason": "사업화 가능성이 높음",
                                      "rankNo": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("FINALIZED"))
                .andExpect(jsonPath("$.data.entries[0].rankNo")
                        .value(1))
                .andExpect(jsonPath("$.data.entries[0].decisionType")
                        .value("MANUAL"))
                .andExpect(jsonPath("$.data.entries[0].decidedByUserId")
                        .value(10))
                .andExpect(jsonPath("$.message")
                        .value("심사 라운드 결과를 확정했습니다."));

        ArgumentCaptor<ReviewRoundFinalizeReq> requestCaptor =
                ArgumentCaptor.forClass(ReviewRoundFinalizeReq.class);
        verify(finalizationService).finalizeRound(
                eq(10L),
                eq("contest-public-id"),
                eq(30L),
                requestCaptor.capture()
        );
        assertThat(requestCaptor.getValue().manualDecisions())
                .singleElement()
                .extracting(decision -> decision.rankNo())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("수동 판정 순위는 양수여야 한다")
    void finalizeRound_rejectsNonPositiveManualRank() throws Exception {
        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}"
                                + "/review-rounds/{roundId}/finalize",
                        "contest-public-id",
                        30L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "manualDecisions": [
                                    {
                                      "entryId": 100,
                                      "status": "SELECTED",
                                      "reason": "수동 선정",
                                      "rankNo": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(finalizationService);
    }

    private HandlerMethodArgumentResolver authPrincipalResolver(
            AuthPrincipal principal
    ) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(
                    MethodParameter parameter
            ) {
                return AuthPrincipal.class.isAssignableFrom(
                        parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return principal;
            }
        };
    }
}
