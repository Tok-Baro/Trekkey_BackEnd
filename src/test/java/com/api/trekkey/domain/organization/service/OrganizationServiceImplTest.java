package com.api.trekkey.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.organization.web.dto.OrganizationSearchRes;
import com.api.trekkey.global.exception.CustomException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "한", " 한 "})
    @DisplayName("검색어가 없거나 2글자 미만이면 예외를 던진다")
    void searchActiveOrganizations_throwsWhenKeywordIsTooShort(String keyword) {
        assertThatThrownBy(() -> organizationService.searchActiveOrganizations(keyword))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(OrganizationErrorResponseCode.ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT);

        verifyNoInteractions(organizationRepository);
    }

    @Test
    @DisplayName("검색어를 trim한 뒤 ACTIVE 학교를 검색하고 응답 DTO로 변환한다")
    void searchActiveOrganizations_searchesWithTrimmedKeyword() {
        Organization organization = organization(1L, "한성대학교");
        given(organizationRepository.findAllByStatusAndNameContainingIgnoreCase(
                OrganizationStatus.ACTIVE,
                "한성"))
                .willReturn(List.of(organization));

        List<OrganizationSearchRes> result = organizationService.searchActiveOrganizations("  한성  ");

        assertThat(result).containsExactly(new OrganizationSearchRes(1L, "한성대학교"));
        verify(organizationRepository).findAllByStatusAndNameContainingIgnoreCase(
                OrganizationStatus.ACTIVE,
                "한성");
    }

    private Organization organization(Long id, String name) {
        Organization organization = mock(Organization.class);
        given(organization.getId()).willReturn(id);
        given(organization.getName()).willReturn(name);
        return organization;
    }
}
