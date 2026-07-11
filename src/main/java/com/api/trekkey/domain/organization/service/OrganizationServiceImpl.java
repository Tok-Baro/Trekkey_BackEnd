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
    @Transactional(readOnly = true)
    public List<OrganizationSearchRes> searchActiveOrganizations(String keyword) {

        //입력받은 keyword가 null이거나 비어있다면 예외처리
        if (keyword == null || keyword.isEmpty()) {
            throw new CustomException(OrganizationErrorResponseCode.ORGANIZATION_SEARCH_KEYWORD_TOO_SHORT);
        }

        //입력받은 keyword가 2글자 미만이면 예외처리
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
