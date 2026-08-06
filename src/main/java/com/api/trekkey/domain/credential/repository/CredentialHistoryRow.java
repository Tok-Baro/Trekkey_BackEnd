package com.api.trekkey.domain.credential.repository;

import java.time.LocalDateTime;

/**
 * 개인 이력 목록용 projection — payload_json 전체를 로드하지 않고
 * 대회명만 SQL(JSON_EXTRACT)로 뽑는다. credentialId가 null이면 끊어진 참조다.
 */
public interface CredentialHistoryRow {

    Long getCredentialId();

    Long getSubjectCredentialId();

    Long getIssuerOrganizationId();

    String getCredentialPublicId();

    String getCredentialNo();

    String getCredentialType();

    String getStatus();

    String getRoleCode();

    String getDisplayName();

    String getContestTitle();

    LocalDateTime getIssuedAt();
}
