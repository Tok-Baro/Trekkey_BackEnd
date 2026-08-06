package com.api.trekkey.domain.review.admin.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.admin.service.ReviewRoundAdminService;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundDeadlineExtendReq;
import com.api.trekkey.domain.review.admin.web.dto.request.ReviewRoundSaveReq;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundCriterionRes;
import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundRes;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class ReviewRoundAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewRoundAdminService reviewRoundAdminService;

    @BeforeEach
    void setUp() {
        ReviewRoundAdminController controller =
                new ReviewRoundAdminController(reviewRoundAdminService);
        AuthPrincipal principal =
                AuthPrincipal.of(10L, "admin@test.com", List.of("ADMIN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver(principal))
                .build();
    }

    @Test
    @DisplayName("심사 라운드를 생성하면 201과 PREPARING 상태를 반환한다")
    void createRound_returnsCreated() throws Exception {
        given(reviewRoundAdminService.createRound(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("contest-public-id"),
                any(ReviewRoundSaveReq.class)
        )).willReturn(roundRes(ReviewRoundStatus.PREPARING));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds",
                        "contest-public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_201"))
                .andExpect(jsonPath("$.data.id").value(40L))
                .andExpect(jsonPath("$.data.roundNo").value(1))
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.criteria[0].code")
                        .value("creativity"));

        verify(reviewRoundAdminService).createRound(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("contest-public-id"),
                any(ReviewRoundSaveReq.class)
        );
    }

    @Test
    @DisplayName("대회의 심사 라운드 목록을 조회한다")
    void getRounds_returnsOk() throws Exception {
        given(reviewRoundAdminService.getRounds(
                10L,
                "contest-public-id"
        )).willReturn(List.of(roundRes(ReviewRoundStatus.PREPARING)));

        mockMvc.perform(get(
                        "/api/admin/contests/{publicId}/review-rounds",
                        "contest-public-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(40L))
                .andExpect(jsonPath("$.data[0].name").value("예선 심사"));

        verify(reviewRoundAdminService)
                .getRounds(10L, "contest-public-id");
    }

    @Test
    @DisplayName("심사 라운드 설정을 수정한다")
    void updateRound_returnsOk() throws Exception {
        given(reviewRoundAdminService.updateRound(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("contest-public-id"),
                org.mockito.ArgumentMatchers.eq(40L),
                any(ReviewRoundSaveReq.class)
        )).willReturn(roundRes(ReviewRoundStatus.PREPARING));

        mockMvc.perform(put(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}",
                        "contest-public-id",
                        40L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("심사 라운드 설정을 저장했습니다."))
                .andExpect(jsonPath("$.data.status").value("PREPARING"));

        verify(reviewRoundAdminService).updateRound(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("contest-public-id"),
                org.mockito.ArgumentMatchers.eq(40L),
                any(ReviewRoundSaveReq.class)
        );
    }

    @Test
    @DisplayName("별도 오픈 API로만 심사 라운드를 OPEN 상태로 전환한다")
    void openRound_returnsOk() throws Exception {
        given(reviewRoundAdminService.openRound(
                10L,
                "contest-public-id",
                40L
        )).willReturn(roundRes(ReviewRoundStatus.OPEN));

        mockMvc.perform(post(
                        "/api/admin/contests/{publicId}/review-rounds/{roundId}/open",
                        "contest-public-id",
                        40L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("심사 라운드를 시작했습니다."))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        verify(reviewRoundAdminService)
                .openRound(10L, "contest-public-id", 40L);
    }

    @Test
    @DisplayName("진행 중인 심사 라운드의 종료 시각을 연장한다")
    void extendDeadline_returnsOk() throws Exception {
        given(reviewRoundAdminService.extendDeadline(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("contest-public-id"),
                org.mockito.ArgumentMatchers.eq(40L),
                any(ReviewRoundDeadlineExtendReq.class)
        )).willReturn(roundRes(ReviewRoundStatus.OPEN));

        mockMvc.perform(patch(
                        "/api/admin/contests/{publicId}"
                                + "/review-rounds/{roundId}/deadline",
                        "contest-public-id",
                        40L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endsAt": "2026-08-03T18:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("심사 라운드 종료 시각을 연장했습니다."))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        verify(reviewRoundAdminService).extendDeadline(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("contest-public-id"),
                org.mockito.ArgumentMatchers.eq(40L),
                org.mockito.ArgumentMatchers.argThat(req ->
                        req.endsAt().equals(LocalDateTime.of(
                                2026, 8, 3, 18, 0)))
        );
    }

    @Test
    @DisplayName("새 종료 시각이 없으면 서비스 호출 전에 400을 반환한다")
    void extendDeadline_rejectsMissingDeadline() throws Exception {
        mockMvc.perform(patch(
                        "/api/admin/contests/{publicId}"
                                + "/review-rounds/{roundId}/deadline",
                        "contest-public-id",
                        40L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(
                reviewRoundAdminService);
    }

    private String validRequestJson() {
        return """
                {
                  "roundNo": 1,
                  "name": "예선 심사",
                  "startsAt": "2026-08-01T09:00:00",
                  "endsAt": "2026-08-02T18:00:00",
                  "targetType": "ALL_SUBMISSIONS",
                  "decisionRule": "TOP_N",
                  "selectCount": 10,
                  "criteria": [
                    {
                      "code": "creativity",
                      "label": "창의성",
                      "maxScore": 50,
                      "sortOrder": 1
                    }
                  ]
                }
                """;
    }

    private ReviewRoundRes roundRes(ReviewRoundStatus status) {
        return new ReviewRoundRes(
                40L,
                1,
                "예선 심사",
                status,
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 2, 18, 0),
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundDecisionRule.TOP_N,
                10,
                null,
                null,
                List.of(new ReviewRoundCriterionRes(
                        50L,
                        "creativity",
                        "창의성",
                        50,
                        1,
                        true
                )),
                LocalDateTime.of(2026, 7, 27, 10, 0),
                LocalDateTime.of(2026, 7, 27, 10, 0)
        );
    }

    private HandlerMethodArgumentResolver authPrincipalResolver(
            AuthPrincipal principal
    ) {
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
                    WebDataBinderFactory binderFactory
            ) {
                return principal;
            }
        };
    }
}
