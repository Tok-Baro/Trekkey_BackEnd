package com.api.trekkey.domain.credential.repository;

import java.time.LocalDateTime;

// 대회·팀 단위 발급 현황 projection — 다형성 source FK를 팀으로 역해석해 한 방에 조회 (erd-mvp §13)
public interface CredentialSummaryRow {

    String getCredentialPublicId();

    String getCredentialNo();

    String getCredentialType();

    String getStatus();

    String getSourceType();

    String getTeamPublicId();

    String getTeamName();

    LocalDateTime getIssuedAt();
}
