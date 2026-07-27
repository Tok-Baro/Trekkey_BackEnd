package com.api.trekkey.domain.review.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.service.ReviewAccessService;
import com.api.trekkey.domain.review.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewAccessRes;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
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
class ReviewAccessControllerTest {

    private static final String RAW_TOKEN = "a".repeat(43);

    private MockMvc mockMvc;

    @Mock
    private ReviewAccessService reviewAccessService;

    @BeforeEach
    void setUp() {
        ReviewAccessController controller =
                new ReviewAccessController(reviewAccessService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("활성 심사 링크를 확인하면 심사위원과 대회 표시 정보만 반환한다")
    void verifyAccess_returnsJudgeContextWithoutToken() throws Exception {
        ReviewAccessReq request = new ReviewAccessReq(RAW_TOKEN);
        given(reviewAccessService.verifyAccess(request))
                .willReturn(new ReviewAccessRes(
                        "김심사",
                        "외부 전문가",
                        "contest-public-id",
                        "AI 공모전",
                        LocalDateTime.of(2026, 8, 1, 0, 0)
                ));

        MvcResult result = mockMvc.perform(post("/api/review/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + RAW_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.message").value("심사 링크를 확인했습니다."))
                .andExpect(jsonPath("$.data.judgeName").value("김심사"))
                .andExpect(jsonPath("$.data.contestPublicId").value("contest-public-id"))
                .andExpect(jsonPath("$.data.contestTitle").value("AI 공모전"))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.judgeId").doesNotExist())
                .andExpect(jsonPath("$.data.reviewTokenHash").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(RAW_TOKEN);
        verify(reviewAccessService).verifyAccess(request);
    }

    @Test
    @DisplayName("유효하지 않은 심사 링크는 401을 반환한다")
    void verifyAccess_rejectsInvalidLink() throws Exception {
        given(reviewAccessService.verifyAccess(new ReviewAccessReq("short")))
                .willThrow(new CustomException(ReviewErrorResponseCode.REVIEW_LINK_INVALID));

        mockMvc.perform(post("/api/review/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"short\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REVIEW_LINK_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("심사 토큰이 누락되어도 값 노출 없이 401을 반환한다")
    void verifyAccess_rejectsMissingToken() throws Exception {
        given(reviewAccessService.verifyAccess(new ReviewAccessReq(null)))
                .willThrow(new CustomException(ReviewErrorResponseCode.REVIEW_LINK_INVALID));

        mockMvc.perform(post("/api/review/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REVIEW_LINK_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

}
