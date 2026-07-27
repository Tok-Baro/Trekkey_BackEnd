package com.api.trekkey.domain.review.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.service.ReviewSheetService;
import com.api.trekkey.domain.review.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetAssignmentRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetCriterionRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewSheetStageRes;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
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
class ReviewSheetControllerTest {

    private static final String RAW_TOKEN = "a".repeat(43);

    private MockMvc mockMvc;

    @Mock
    private ReviewSheetService reviewSheetService;

    @BeforeEach
    void setUp() {
        ReviewSheetController controller =
                new ReviewSheetController(reviewSheetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("토큰으로 평가표를 조회하면 배정 정보만 반환하고 캐시를 금지한다")
    void getReviewSheet_returnsAssignmentsWithoutToken() throws Exception {
        ReviewAccessReq request = new ReviewAccessReq(RAW_TOKEN);
        given(reviewSheetService.getReviewSheet(request))
                .willReturn(reviewSheetRes());

        MvcResult result = mockMvc.perform(post("/api/review/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + RAW_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.message")
                        .value("심사 평가표를 조회했습니다."))
                .andExpect(jsonPath("$.data.judgeName").value("김심사"))
                .andExpect(jsonPath("$.data.roleLabel")
                        .value("외부 전문가"))
                .andExpect(jsonPath("$.data.contestPublicId")
                        .value("contest-public-id"))
                .andExpect(jsonPath("$.data.contestTitle")
                        .value("AI 공모전"))
                .andExpect(jsonPath("$.data.stages[0].reviewStageId")
                        .value(300L))
                .andExpect(jsonPath("$.data.stages[0].stageName")
                        .value("1차 심사"))
                .andExpect(jsonPath("$.data.stages[0].criteria[0].id")
                        .value(600L))
                .andExpect(jsonPath("$.data.stages[0].criteria[0].code")
                        .value("creativity"))
                .andExpect(jsonPath("$.data.stages[0].criteria[0].maxScore")
                        .value(30))
                .andExpect(jsonPath(
                        "$.data.stages[0].assignments[0].assignmentId")
                        .value(500L))
                .andExpect(jsonPath(
                        "$.data.stages[0].assignments[0].submissionPublicId")
                        .value("submission-public-id"))
                .andExpect(jsonPath(
                        "$.data.stages[0].assignments[0].status")
                        .value("ASSIGNED"))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.judgeId").doesNotExist())
                .andExpect(jsonPath("$.data.reviewTokenHash").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(RAW_TOKEN);
        verify(reviewSheetService).getReviewSheet(request);
    }

    @Test
    @DisplayName("유효하지 않은 심사 링크로 평가표를 조회하면 401을 반환한다")
    void getReviewSheet_rejectsInvalidLink() throws Exception {
        ReviewAccessReq request = new ReviewAccessReq("short");
        given(reviewSheetService.getReviewSheet(request))
                .willThrow(new CustomException(
                        ReviewErrorResponseCode.REVIEW_LINK_INVALID));

        MvcResult result = mockMvc.perform(post("/api/review/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"short\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_LINK_INVALID"))
                .andExpect(jsonPath("$.httpStatus").value(401))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("short");
        verify(reviewSheetService).getReviewSheet(request);
    }

    private ReviewSheetRes reviewSheetRes() {
        return new ReviewSheetRes(
                "김심사",
                "외부 전문가",
                "contest-public-id",
                "AI 공모전",
                LocalDateTime.of(2099, 8, 1, 18, 0),
                List.of(new ReviewSheetStageRes(
                        300L,
                        "1차 심사",
                        LocalDateTime.of(2026, 7, 24, 9, 0),
                        LocalDateTime.of(2099, 8, 1, 18, 0),
                        List.of(new ReviewSheetCriterionRes(
                                600L,
                                "creativity",
                                "창의성",
                                30,
                                1
                        )),
                        List.of(new ReviewSheetAssignmentRes(
                                500L,
                                "submission-public-id",
                                "AI 작품",
                                ReviewAssignmentStatus.ASSIGNED,
                                LocalDateTime.of(2099, 8, 1, 18, 0),
                                null
                        ))
                ))
        );
    }
}
