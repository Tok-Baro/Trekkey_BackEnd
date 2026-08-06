package com.api.trekkey.domain.review.admin.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.admin.service.ReviewRecordAdminService;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRecordRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRecordScoreRes;
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
class ReviewRecordAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewRecordAdminService reviewRecordAdminService;

    @BeforeEach
    void setUp() {
        ReviewRecordAdminController controller =
                new ReviewRecordAdminController(reviewRecordAdminService);
        AuthPrincipal principal =
                AuthPrincipal.of(10L, "admin@test.com", List.of("ADMIN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        authPrincipalResolver(principal))
                .build();
    }

    @Test
    @DisplayName("라운드의 심사 세부 기록을 조회한다")
    void getReviews_returnsOk() throws Exception {
        given(reviewRecordAdminService.getReviews(
                10L,
                "contest-public-id",
                40L
        )).willReturn(List.of(reviewRecord()));

        mockMvc.perform(get(
                        "/api/admin/contests/{publicId}"
                                + "/review-rounds/{roundId}/reviews",
                        "contest-public-id",
                        40L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.data[0].reviewId")
                        .value(80L))
                .andExpect(jsonPath("$.data[0].assignmentId")
                        .value(70L))
                .andExpect(jsonPath("$.data[0].reviewRoundId")
                        .value(40L))
                .andExpect(jsonPath("$.data[0].judgeName")
                        .value("김심사"))
                .andExpect(jsonPath("$.data[0].submissionPublicId")
                        .value("submission-public-id"))
                .andExpect(jsonPath("$.data[0].teamName")
                        .value("트랙키 팀"))
                .andExpect(jsonPath("$.data[0].totalScore")
                        .value(92.5))
                .andExpect(jsonPath("$.data[0].scores[0].code")
                        .value("creativity"))
                .andExpect(jsonPath("$.data[0].scores[0].score")
                        .value(45.5));

        verify(reviewRecordAdminService).getReviews(
                10L,
                "contest-public-id",
                40L
        );
    }

    @Test
    @DisplayName("심사 세부 기록이 없으면 200과 빈 배열을 반환한다")
    void getReviews_returnsEmptyArray() throws Exception {
        given(reviewRecordAdminService.getReviews(
                10L,
                "contest-public-id",
                40L
        )).willReturn(List.of());

        mockMvc.perform(get(
                        "/api/admin/contests/{publicId}"
                                + "/review-rounds/{roundId}/reviews",
                        "contest-public-id",
                        40L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(reviewRecordAdminService).getReviews(
                10L,
                "contest-public-id",
                40L
        );
    }

    private ReviewRecordRes reviewRecord() {
        return new ReviewRecordRes(
                80L,
                70L,
                40L,
                50L,
                60L,
                "김심사",
                "외부 전문가",
                "submission-public-id",
                "AI 캠퍼스",
                "트랙키 팀",
                new BigDecimal("92.50"),
                "좋은 작품입니다.",
                LocalDateTime.of(2026, 8, 4, 15, 30),
                List.of(new ReviewRecordScoreRes(
                        90L,
                        "creativity",
                        "창의성",
                        50,
                        1,
                        new BigDecimal("45.50")
                ))
        );
    }

    private HandlerMethodArgumentResolver authPrincipalResolver(
            AuthPrincipal principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return AuthPrincipal.class.isAssignableFrom(
                        parameter.getParameterType());
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
