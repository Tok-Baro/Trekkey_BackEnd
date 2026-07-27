package com.api.trekkey.domain.credential.web.dto;

import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import java.time.LocalDateTime;

/**
 * 개인 Credential 이력 한 건 (erd-mvp §5 — 현재 팀이 아니라 발급 당시 subject snapshot 기준).
 * credentialPublicId는 공개 검증 페이지 링크에 사용한다.
 */
public record CredentialHistoryRes(
        String credentialPublicId,
        String credentialNo,
        CredentialType credentialType,
        CredentialStatus status,
        String roleCode,
        String displayName,
        String contestTitle,
        LocalDateTime issuedAt) {
}
