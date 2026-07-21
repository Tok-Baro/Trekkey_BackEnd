package com.api.trekkey.domain.audit.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AdminAuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK 대신 값 저장 — 유저가 삭제돼도 감사 로그는 보존한다.
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 60)
    private String action; //GitHub식 `카테고리.행위` 네이밍 (예: invitation.issue)

    @Column(length = 30)
    private String targetType;

    private Long targetId;

    @Column(length = 500)
    private String detail; //요약 — 비밀번호·토큰·초대 URL 절대 미포함

    @Column(length = 45)
    private String clientIp; //IPv6 최대 45자
}
