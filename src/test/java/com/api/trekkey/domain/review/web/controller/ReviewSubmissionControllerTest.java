package com.api.trekkey.domain.review.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.service.ReviewSubmissionService;
import com.api.trekkey.domain.review.web.dto.request.ReviewScoreReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewSubmitReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewScoreItemRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSubmitRes;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReviewSubmissionControllerTest {

    private static final Long ASSIGNMENT_ID = 500L;
    private static final String RAW_TOKEN = "a".repeat(43);

    private MockMvc mockMvc;

    @Mock
    private ReviewSubmissionService reviewSubmissionService;

    @BeforeEach
    void setUp() {
        ReviewSubmissionController controller =
                new ReviewSubmissionController(reviewSubmissionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("토큰으로 채점을 제출하면 서버 계산 결과를 반환하고 응답 캐시를 금지한다")
    void submitReview_returnsResultWithoutTokenAndDisablesCaching()
            throws Exception {
        ReviewSubmitReq request = validRequest();
        assertThat(request.toString())
                .doesNotContain(RAW_TOKEN)
                .doesNotContain(request.comment())
                .contains("token=***", "comment=***");
        given(reviewSubmissionService.submitReview(
                ASSIGNMENT_ID,
                request
        )).willReturn(response());

        MvcResult result = mockMvc.perform(put(
                        "/api/review/assignments/{assignmentId}/review",
                        ASSIGNMENT_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.message")
                        .value("채점 결과를 제출했습니다."))
                .andExpect(jsonPath("$.data.reviewId").value(700L))
                .andExpect(jsonPath("$.data.assignmentId")
                        .value(ASSIGNMENT_ID))
                .andExpect(jsonPath("$.data.totalScore").value(45.5))
                .andExpect(jsonPath("$.data.comment")
                        .value("아이디어가 참신합니다."))
                .andExpect(jsonPath("$.data.submittedAt[0]")
                        .value(2026))
                .andExpect(jsonPath("$.data.scores").isArray())
                .andExpect(jsonPath("$.data.scores.length()").value(2))
                .andExpect(jsonPath("$.data.scores[0].criterionId")
                        .value(600L))
                .andExpect(jsonPath("$.data.scores[0].code")
                        .value("creativity"))
                .andExpect(jsonPath("$.data.scores[0].label")
                        .value("창의성"))
                .andExpect(jsonPath("$.data.scores[0].maxScore")
                        .value(30))
                .andExpect(jsonPath("$.data.scores[0].sortOrder")
                        .value(1))
                .andExpect(jsonPath("$.data.scores[0].score")
                        .value(25.5))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(RAW_TOKEN);
        verify(reviewSubmissionService).submitReview(
                ASSIGNMENT_ID,
                request
        );
    }

    @Test
    @DisplayName("이미 제출한 채점과 다른 요청이면 도메인 오류 409를 반환한다")
    void submitReview_returnsConflictForDifferentRetry()
            throws Exception {
        ReviewSubmitReq request = validRequest();
        given(reviewSubmissionService.submitReview(
                ASSIGNMENT_ID,
                request
        )).willThrow(new CustomException(
                ReviewErrorResponseCode.REVIEW_ALREADY_SUBMITTED));

        MvcResult result = mockMvc.perform(put(
                        "/api/review/assignments/{assignmentId}/review",
                        ASSIGNMENT_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_ALREADY_SUBMITTED"))
                .andExpect(jsonPath("$.httpStatus").value(409))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(RAW_TOKEN);
        verify(reviewSubmissionService).submitReview(
                ASSIGNMENT_ID,
                request
        );
    }

    @Test
    @DisplayName("평가 점수 목록이 비어 있으면 서비스 호출 없이 400을 반환한다")
    void submitReview_rejectsEmptyScores() throws Exception {
        mockMvc.perform(put(
                        "/api/review/assignments/{assignmentId}/review",
                        ASSIGNMENT_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "scores": [],
                                  "comment": "의견"
                                }
                                """.formatted(RAW_TOKEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.data[*].field")
                        .value(hasItem("scores")));

        verifyNoInteractions(reviewSubmissionService);
    }

    @Test
    @DisplayName("중첩 점수 항목의 필수 값이 없으면 서비스 호출 없이 400을 반환한다")
    void submitReview_rejectsInvalidNestedScore() throws Exception {
        mockMvc.perform(put(
                        "/api/review/assignments/{assignmentId}/review",
                        ASSIGNMENT_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "scores": [
                                    {
                                      "criterionId": null,
                                      "score": null
                                    }
                                  ],
                                  "comment": "의견"
                                }
                                """.formatted(RAW_TOKEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.data[*].field")
                        .value(hasItem("scores[0].criterionId")))
                .andExpect(jsonPath("$.data[*].field")
                        .value(hasItem("scores[0].score")));

        verifyNoInteractions(reviewSubmissionService);
    }

    private ReviewSubmitReq validRequest() {
        return new ReviewSubmitReq(
                RAW_TOKEN,
                List.of(
                        new ReviewScoreReq(
                                600L,
                                new BigDecimal("25.5")
                        ),
                        new ReviewScoreReq(
                                601L,
                                new BigDecimal("20")
                        )
                ),
                "아이디어가 참신합니다."
        );
    }

    private String validRequestJson() {
        return """
                {
                  "token": "%s",
                  "scores": [
                    {
                      "criterionId": 600,
                      "score": 25.5
                    },
                    {
                      "criterionId": 601,
                      "score": 20
                    }
                  ],
                  "comment": "아이디어가 참신합니다."
                }
                """.formatted(RAW_TOKEN);
    }

    private ReviewSubmitRes response() {
        return new ReviewSubmitRes(
                700L,
                ASSIGNMENT_ID,
                new BigDecimal("45.50"),
                "아이디어가 참신합니다.",
                LocalDateTime.of(2026, 7, 27, 14, 30),
                List.of(
                        new ReviewScoreItemRes(
                                600L,
                                "creativity",
                                "창의성",
                                30,
                                1,
                                new BigDecimal("25.50")
                        ),
                        new ReviewScoreItemRes(
                                601L,
                                "feasibility",
                                "실현 가능성",
                                20,
                                2,
                                new BigDecimal("20.00")
                        )
                )
        );
    }
}
