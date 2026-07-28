package com.api.trekkey.domain.organization.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrganizationTest {

    @Test
    @DisplayName("기관 공개 ID는 최초 생성 후 변경되지 않는다")
    void ensurePublicId_returnsStableValue() {
        Organization organization = new Organization();

        String publicId = organization.ensurePublicId();

        assertThat(publicId).isNotBlank();
        assertThat(organization.ensurePublicId()).isEqualTo(publicId);
    }

    @Test
    @DisplayName("공백 공개 ID는 저장 가능한 UUID로 교체한다")
    void ensurePublicId_replacesBlankValue() {
        Organization organization = new Organization();
        ReflectionTestUtils.setField(organization, "publicId", " ");

        assertThat(organization.ensurePublicId()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }
}
