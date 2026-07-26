package com.api.trekkey.global.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.contest.publicapi.service.ContestService;
import com.api.trekkey.domain.contest.publicapi.web.controller.ContestController;
import com.api.trekkey.domain.contest.publicapi.web.dto.ContestSearchStatus;
import com.api.trekkey.domain.team.publicapi.service.TeamApplicationService;
import com.api.trekkey.domain.team.publicapi.web.controller.TeamApplicationController;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.handler.JwtAccessDeniedHandler;
import com.api.trekkey.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.trekkey.global.security.jwt.JwtAuthenticationFilter;
import com.api.trekkey.global.security.jwt.JwtExtractor;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = {ContestController.class, TeamApplicationController.class},
        properties = "security.jwt.secret-key="
                + "c2VjdXJpdHktY29uZmlnLXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtbG9uZy0xMjM0NTY3ODkwYWJjZGVm")
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtExtractor.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ContestService contestService;

    @MockitoBean
    private TeamApplicationService teamApplicationService;

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("대회 상세는 인증 없이 조회할 수 없다")
    void contestDetail_rejectsAnonymous() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";

        mockMvc.perform(get("/api/contests/{publicId}", publicId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(contestService);
    }

    @Test
    @DisplayName("참가자는 대회 상세를 조회할 수 있다")
    void contestDetail_permitsParticipant() throws Exception {
        String publicId = "f04739b5-bb66-4c3f-bf91-31b8712011be";
        given(contestService.getContestDetail(10L, publicId)).willReturn(null);

        mockMvc.perform(get("/api/contests/{publicId}", publicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("대회 목록은 인증 없이 조회할 수 없다")
    void contestList_rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/contests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(contestService);
    }

    @Test
    @DisplayName("참가자는 대회 목록을 조회할 수 있다")
    void contestList_permitsParticipant() throws Exception {
        given(contestService.searchContests(10L, null, ContestSearchStatus.OPEN))
                .willReturn(List.of());

        mockMvc.perform(get("/api/contests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("참가 신청은 인증 없이 요청할 수 없다")
    void contestApplication_rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(teamApplicationService);
    }

    @Test
    @DisplayName("참가자는 대회에 참가 신청할 수 있다")
    void contestApplication_permitsParticipant() throws Exception {
        String publicId = "public-id";

        mockMvc.perform(post("/api/contests/{publicId}/applications", publicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("PARTICIPANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS_201"));

        verify(teamApplicationService).createApplication(
                10L,
                publicId,
                new TeamApplicationCreateReq(
                        "트랙키 팀",
                        "홍길동",
                        "컴퓨터공학부",
                        3,
                        "hong@example.com",
                        "010-1234-5678",
                        "AI 아이디어를 구현하고 싶습니다."));
    }

    @Test
    @DisplayName("관리자는 참가 신청할 수 없다")
    void contestApplication_rejectsAdmin() throws Exception {
        mockMvc.perform(post("/api/contests/{publicId}/applications", "public-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GLOBAL_403"));

        verifyNoInteractions(teamApplicationService);
    }

    private String validApplicationRequest() {
        return """
                {
                  "teamName": "트랙키 팀",
                  "leaderName": "홍길동",
                  "major": "컴퓨터공학부",
                  "memberCount": 3,
                  "contactEmail": "hong@example.com",
                  "phone": "010-1234-5678",
                  "motivation": "AI 아이디어를 구현하고 싶습니다."
                }
                """;
    }

    private String accessToken(String role) {
        AuthPrincipal principal = AuthPrincipal.of(10L, "participant@example.com", List.of(role));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.createAccessToken(authentication);
    }

}
