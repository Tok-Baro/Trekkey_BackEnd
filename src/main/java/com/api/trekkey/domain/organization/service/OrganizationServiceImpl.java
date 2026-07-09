package com.api.trekkey.domain.organization.service;

import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.organization.web.dto.OrganizationSearchRes;
import com.api.trekkey.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationServiceImpl implements OrganizationService {
    private final OrganizationRepository organizationRepository;

    @Override
    public List<OrganizationSearchRes> searchActiveOrganizations(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            throw new CustomException(OrganizationErrorResponseCode.ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT);
        }

        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.length() < 2) {
            throw new CustomException(OrganizationErrorResponseCode.ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT);
        }

        return organizationRepository.findAllByStatusAndNameContainingIgnoreCase(OrganizationStatus.ACTIVE, trimmedKeyword)
                .stream()
                .map(OrganizationSearchRes::from)
                .toList();
    }
}
