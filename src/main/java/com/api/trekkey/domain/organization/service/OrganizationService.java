package com.api.trekkey.domain.organization.service;

import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.web.dto.OrganizationSearchRes;

import java.util.List;

public interface OrganizationService {

    /*
        searchActiveOrganizations() : 활성상태의 학교를 조회하는 비즈니스 로직 메서드
     */
    List<OrganizationSearchRes> searchActiveOrganizations(String keyword);
}
