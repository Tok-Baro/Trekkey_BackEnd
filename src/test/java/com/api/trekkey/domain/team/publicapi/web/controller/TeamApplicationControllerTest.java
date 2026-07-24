package com.api.trekkey.domain.team.publicapi.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.dto.ParticipantSearchRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationRes;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TeamApplicationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TeamApplicationService teamApplicationService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(participantAuthentication());
        TeamApplicationController controller = new TeamApplicationController(teamApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 참가 신청을 접수하고 201을 반환한다")
    void createApplication_returnsCreatedResponse() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";

        mockMvc.perform(post("/api/contests/{publicId}/applications", publicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_201"))
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.message").value("참가 신청을 접수했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<TeamApplicationCreateReq> requestCaptor =
                ArgumentCaptor.forClass(TeamApplicationCreateReq.class);
        verify(teamApplicationService).createApplication(eq(10L), eq(publicId), requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isEqualTo(new TeamApplicationCreateReq(
                "트랙키 팀",
                "홍길동",
                "컴퓨터공학부",
                List.of(11L, 12L),
                "hong@example.com",
                "010-1234-5678",
                "AI 아이디어를 구현하고 싶습니다."));
    }

    @Test
    @DisplayName("본인의 참가 신청 목록을 SuccessResponse로 반환한다")
    void getMyApplications_returnsSuccessResponse() throws Exception {
        given(teamApplicationService.getMyApplications(10L)).willReturn(List.of(applicationResponse()));

        mockMvc.perform(get("/api/me/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data[0].contestPublicId").value("contest-public-id"))
                .andExpect(jsonPath("$.data[0].contestTitle").value("AI 창의 경진대회"))
                .andExpect(jsonPath("$.data[0].department").value("SW중심대학사업단"))
                .andExpect(jsonPath("$.data[0].participationType").value("TEAM"))
                .andExpect(jsonPath("$.data[0].teamName").value("트랙키 팀"))
                .andExpect(jsonPath("$.data[0].leaderName").value("홍길동"))
                .andExpect(jsonPath("$.data[0].major").value("컴퓨터공학부"))
                .andExpect(jsonPath("$.data[0].memberCount").value(3))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].contactEmail").value("hong@example.com"))
                .andExpect(jsonPath("$.data[0].phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data[0].motivation").value("AI 아이디어를 구현하고 싶습니다."))
                .andExpect(jsonPath("$.data[0].createdAt").exists())
                .andExpect(jsonPath("$.data[0].updatedAt").exists())
                .andExpect(jsonPath("$.data[0].teamPublicId").doesNotExist())
                .andExpect(jsonPath("$.data[0].submissionDueAt").doesNotExist());

        verify(teamApplicationService).getMyApplications(10L);
    }

    @Test
    @DisplayName("본인의 참가 신청이 없으면 빈 배열을 반환한다")
    void getMyApplications_returnsEmptyArray() throws Exception {
        given(teamApplicationService.getMyApplications(10L)).willReturn(List.of());

        mockMvc.perform(get("/api/me/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("대표자를 제외한 팀원이 4명을 초과하면 400을 반환한다")
    void createApplication_returnsBadRequestWhenTooManyMembersAreRequested() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamName": "트랙키 팀",
                                  "leaderName": "홍길동",
                                  "major": "컴퓨터공학부",
                                  "memberUserIds": [11, 12, 13, 14, 15],
                                  "contactEmail": "hong@example.com",
                                  "phone": "010-1234-5678",
                                  "motivation": "AI 아이디어를 구현하고 싶습니다."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.httpStatus").value(400));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("팀원 목록을 누락하면 400을 반환한다")
    void createApplication_returnsBadRequestWhenMemberUserIdsAreMissing() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamName": "트랙키 팀",
                                  "leaderName": "홍길동",
                                  "major": "컴퓨터공학부",
                                  "contactEmail": "hong@example.com",
                                  "phone": "010-1234-5678",
                                  "motivation": "AI 아이디어를 구현하고 싶습니다."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("이미 신청한 대회면 409를 반환한다")
    void createApplication_returnsConflictWhenApplicationAlreadyExists() throws Exception {
        willThrow(new CustomException(TeamErrorResponseCode.TEAM_APPLICATION_ALREADY_EXISTS))
                .given(teamApplicationService)
                .createApplication(any(Long.class), any(String.class), any(TeamApplicationCreateReq.class));

        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("TEAM_APPLICATION_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.httpStatus").value(409));
    }

    @Test
    @DisplayName("같은 학교의 참가자 검색 결과를 반환한다")
    void searchParticipants_returnsSuccessResponse() throws Exception {
        given(teamApplicationService.searchParticipants(10L, "김"))
                .willReturn(List.of(new ParticipantSearchRes(
                        11L,
                        "김팀원",
                        "20260001",
                        "컴퓨터공학부")));

        mockMvc.perform(get("/api/participants/search")
                        .param("keyword", "김"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(11))
                .andExpect(jsonPath("$.data[0].name").value("김팀원"))
                .andExpect(jsonPath("$.data[0].studentId").value("20260001"))
                .andExpect(jsonPath("$.data[0].major").value("컴퓨터공학부"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());

        verify(teamApplicationService).searchParticipants(10L, "김");
    }

    @Test
    @DisplayName("참가자 검색 결과가 없으면 빈 배열을 반환한다")
    void searchParticipants_returnsEmptyArray() throws Exception {
        given(teamApplicationService.searchParticipants(10L, "없는학생")).willReturn(List.of());

        mockMvc.perform(get("/api/participants/search")
                        .param("keyword", "없는학생"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("참가자 검색어를 누락하면 400을 반환한다")
    void searchParticipants_returnsBadRequestWhenKeywordIsMissing() throws Exception {
        mockMvc.perform(get("/api/participants/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(teamApplicationService);
    }

    private String validRequest() {
        return """
                {
                  "teamName": "트랙키 팀",
                  "leaderName": "홍길동",
                  "major": "컴퓨터공학부",
                  "memberUserIds": [11, 12],
                  "contactEmail": "hong@example.com",
                  "phone": "010-1234-5678",
                  "motivation": "AI 아이디어를 구현하고 싶습니다."
                }
                """;
    }

    private TeamApplicationRes applicationResponse() {
        return new TeamApplicationRes(
                "contest-public-id",
                "AI 창의 경진대회",
                "SW중심대학사업단",
                ParticipationType.TEAM,
                "트랙키 팀",
                "홍길동",
                "컴퓨터공학부",
                3,
                TeamStatus.PENDING,
                "hong@example.com",
                "010-1234-5678",
                "AI 아이디어를 구현하고 싶습니다.",
                LocalDateTime.of(2026, 7, 24, 15, 30),
                LocalDateTime.of(2026, 7, 24, 16, 10));
    }

    private Authentication participantAuthentication() {
        AuthPrincipal principal = AuthPrincipal.of(10L, "participant@example.com", List.of("PARTICIPANT"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
