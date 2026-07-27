package com.api.trekkey.domain.credential.web.dto;

import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.repository.CredentialSummaryRow;
import java.time.LocalDateTime;

// 관리자 발급 현황 한 건 (erd-mvp §13) — credentialPublicId는 공개 검증·패키지 링크에 사용
public record CredentialSummaryRes(
        String credentialPublicId,
        String credentialNo,
        CredentialType credentialType,
        CredentialStatus status,
        CredentialSourceType sourceType,
        String teamPublicId,
        String teamName,
        LocalDateTime issuedAt) {

    public static CredentialSummaryRes from(CredentialSummaryRow row) {
        return new CredentialSummaryRes(
                row.getCredentialPublicId(),
                row.getCredentialNo(),
                CredentialType.valueOf(row.getCredentialType()),
                CredentialStatus.valueOf(row.getStatus()),
                CredentialSourceType.valueOf(row.getSourceType()),
                row.getTeamPublicId(),
                row.getTeamName(),
                row.getIssuedAt());
    }
}
