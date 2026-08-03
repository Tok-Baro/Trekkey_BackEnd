package com.api.trekkey.domain.contest.admin.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.contest.admin.service.ContestAdminQueryService;
import com.api.trekkey.domain.contest.admin.service.ContestCommandService;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ContestAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContestCommandService contestCommandService;

    @Mock
    private ContestAdminQueryService contestAdminQueryService;

    @BeforeEach
    void setUp() {
        AuthPrincipal principal =
                AuthPrincipal.of(10L, "admin@test.com", List.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
        ContestAdminController controller = new ContestAdminController(
                contestCommandService,
                contestAdminQueryService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("관리자 대회 상세를 단계와 함께 SuccessResponse로 반환한다")
    void getAdminContest_returnsFullDetail() throws Exception {
        given(contestAdminQueryService.getContest(
                10L,
                "contest-public-id"
        )).willReturn(detailResponse());

        mockMvc.perform(get(
                        "/api/admin/contests/{publicId}",
                        "contest-public-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.data.id")
                        .value("contest-public-id"))
                .andExpect(jsonPath("$.data.status")
                        .value("PREPARING"))
                .andExpect(jsonPath("$.data.department")
                        .value("SW중심대학사업단"))
                .andExpect(jsonPath("$.data.applicationStartsAt")
                        .exists())
                .andExpect(jsonPath("$.data.submissionDueAt")
                        .exists())
                .andExpect(jsonPath("$.data.stages.length()").value(2))
                .andExpect(jsonPath("$.data.stages[0].id").value(201L))
                .andExpect(jsonPath("$.data.stages[0].stageType")
                        .value("APPLICATION"))
                .andExpect(jsonPath("$.data.stages[1].id").value(202L))
                .andExpect(jsonPath("$.data.stages[1].stageType")
                        .value("SUBMISSION"));

        verify(contestAdminQueryService)
                .getContest(10L, "contest-public-id");
    }

    @Test
    @DisplayName("다른 조직의 관리자 대회 상세 요청은 403을 반환한다")
    void getAdminContest_returnsForbidden() throws Exception {
        given(contestAdminQueryService.getContest(
                10L,
                "contest-public-id"
        )).willThrow(new CustomException(
                ContestErrorResponseCode.CONTEST_FORBIDDEN));

        mockMvc.perform(get(
                        "/api/admin/contests/{publicId}",
                        "contest-public-id"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code")
                        .value("CONTEST_FORBIDDEN"));
    }

    private ContestDetailRes detailResponse() {
        LocalDateTime applicationStartsAt =
                LocalDateTime.of(2026, 8, 1, 9, 0);
        LocalDateTime applicationEndsAt =
                LocalDateTime.of(2026, 8, 10, 18, 0);
        LocalDateTime submissionDueAt =
                LocalDateTime.of(2026, 8, 20, 23, 59);
        return new ContestDetailRes(
                "contest-public-id",
                "AI 창의 경진대회",
                "SW중심대학사업단",
                ContestStatus.PREPARING,
                ParticipationType.BOTH,
                3,
                "https://example.com/poster.png",
                "AI로 해결하는 캠퍼스 문제",
                "전체 재학생",
                "온라인 신청",
                "우수팀 시상",
                "AI,캠퍼스",
                "<p>상세 안내</p>",
                31L,
                applicationStartsAt,
                applicationEndsAt,
                submissionDueAt,
                List.of(
                        new StageRes(
                                201L,
                                "참가 신청",
                                StageType.APPLICATION,
                                1,
                                StageStatus.PREPARING,
                                applicationStartsAt,
                                applicationEndsAt,
                                null,
                                null,
                                null,
                                null,
                                List.of()
                        ),
                        new StageRes(
                                202L,
                                "작품 제출",
                                StageType.SUBMISSION,
                                2,
                                StageStatus.PREPARING,
                                LocalDateTime.of(2026, 8, 11, 9, 0),
                                submissionDueAt,
                                null,
                                null,
                                null,
                                null,
                                List.of()
                        )
                )
        );
    }
}
