package com.api.trekkey.domain.organization.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrganizationTest {

    @Test
    @DisplayName("기관 공개 ID는 최초 생성 후 변경되지 않는다")
    void ensurePublicId_returnsStableValue() {
        Organization organization = new Organization();

        String publicId = organization.ensurePublicId();

        assertThat(publicId).isNotBlank();
        assertThat(organization.ensurePublicId()).isEqualTo(publicId);
    }
}
