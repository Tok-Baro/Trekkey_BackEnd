package com.api.trekkey.domain.review.publicapi.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.publicapi.service.ReviewFileService;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReviewFileControllerTest {

    private static final String RAW_TOKEN = "a".repeat(43);

    private MockMvc mockMvc;

    @Mock
    private ReviewFileService reviewFileService;

    @BeforeEach
    void setUp() {
        ReviewFileController controller =
                new ReviewFileController(reviewFileService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("심사 파일을 토큰으로 다운로드하면 원본 메타데이터와 캐시 금지 헤더를 반환한다")
    void downloadFile_returnsStreamWithSafeHeaders() throws Exception {
        byte[] content = "review-file".getBytes(StandardCharsets.UTF_8);
        ReviewAccessReq request = new ReviewAccessReq(RAW_TOKEN);
        given(reviewFileService.downloadFile(10L, request))
                .willReturn(new FileDownload(
                        "작품.pdf",
                        "application/pdf",
                        content.length,
                        new ByteArrayInputStream(content)
                ));

        MvcResult result = mockMvc.perform(post(
                                "/api/review/files/{fileId}/download",
                                10L
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + RAW_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(header().string(
                        HttpHeaders.PRAGMA,
                        "no-cache"
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "application/pdf"
                ))
                .andExpect(header().longValue(
                        HttpHeaders.CONTENT_LENGTH,
                        content.length
                ))
                .andReturn();

        ContentDisposition disposition = ContentDisposition.parse(
                result.getResponse().getHeader(
                        HttpHeaders.CONTENT_DISPOSITION)
        );
        assertThat(disposition.getType()).isEqualTo("attachment");
        assertThat(disposition.getFilename()).isEqualTo("작품.pdf");
        assertThat(result.getResponse().getContentAsByteArray())
                .isEqualTo(content);
        verify(reviewFileService).downloadFile(10L, request);
    }

    @Test
    @DisplayName("배정되지 않은 파일은 상세 정보를 노출하지 않고 404를 반환한다")
    void downloadFile_rejectsUnassignedFile() throws Exception {
        ReviewAccessReq request = new ReviewAccessReq(RAW_TOKEN);
        given(reviewFileService.downloadFile(10L, request))
                .willThrow(new CustomException(
                        ReviewErrorResponseCode.REVIEW_ASSIGNMENT_NOT_FOUND
                ));

        MvcResult result = mockMvc.perform(post(
                                "/api/review/files/{fileId}/download",
                                10L
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + RAW_TOKEN + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_ASSIGNMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(RAW_TOKEN);
        verify(reviewFileService).downloadFile(10L, request);
    }
}
