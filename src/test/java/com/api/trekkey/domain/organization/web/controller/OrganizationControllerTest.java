package com.api.trekkey.domain.organization.web.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.organization.service.OrganizationService;
import com.api.trekkey.domain.organization.web.dto.OrganizationSearchRes;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        OrganizationController organizationController = new OrganizationController(organizationService);
        mockMvc = MockMvcBuilders.standaloneSetup(organizationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("학교 검색 결과를 SuccessResponse로 반환한다")
    void searchActiveOrganizations_returnsSuccessResponse() throws Exception {
        given(organizationService.searchActiveOrganizations("한성"))
                .willReturn(List.of(new OrganizationSearchRes(1L, "한성대학교")));

        mockMvc.perform(get("/api/organizations").param("keyword", "한성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("한성대학교"));
    }

    @Test
    @DisplayName("검색어가 없으면 400 응답을 반환한다")
    void searchActiveOrganizations_returnsBadRequestWhenKeywordIsMissing() throws Exception {
        given(organizationService.searchActiveOrganizations(null))
                .willThrow(new CustomException(OrganizationErrorResponseCode.ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT));

        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT"))
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.message").value("학교이름을 2글자 이상 입력해주세요"));
    }

    @Test
    @DisplayName("검색어가 2글자 미만이면 400 응답을 반환한다")
    void searchActiveOrganizations_returnsBadRequestWhenKeywordIsTooShort() throws Exception {
        given(organizationService.searchActiveOrganizations("한"))
                .willThrow(new CustomException(OrganizationErrorResponseCode.ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT));

        mockMvc.perform(get("/api/organizations").param("keyword", "한"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT"))
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.message").value("학교이름을 2글자 이상 입력해주세요"));
    }
}
