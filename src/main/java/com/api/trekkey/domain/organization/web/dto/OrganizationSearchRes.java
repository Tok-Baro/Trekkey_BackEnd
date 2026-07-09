package com.api.trekkey.domain.organization.web.dto;

import com.api.trekkey.domain.organization.entity.Organization;

public record OrganizationSearchRes(
        Long id,
        String name
) {
    public static OrganizationSearchRes from(Organization organization) {
        return new OrganizationSearchRes(
                organization.getId(),
                organization.getName()
        );
    }
}
