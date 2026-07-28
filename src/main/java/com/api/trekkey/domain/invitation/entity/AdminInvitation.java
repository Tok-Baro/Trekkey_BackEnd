package com.api.trekkey.domain.invitation.entity;

import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
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
public class AdminInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 100)
    private String email; //초대 대상 이메일 (가입 시 일치 검증)

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash; //SHA-256 해시 — 초대 토큰 원문은 저장하지 않는다

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.ISSUED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedBy; //발급한 ROOT_ADMIN

    @Column(nullable = false)
    private LocalDateTime expiresAt; //발급 + 7일

    private LocalDateTime usedAt;

    // 가입에 사용됨
    public void use(LocalDateTime now) {
        this.status = InvitationStatus.USED;
        this.usedAt = now;
    }

    // ROOT_ADMIN 철회 (초대 URL 유출 대응)
    public void revoke() {
        this.status = InvitationStatus.REVOKED;
    }

    // 만료 처리
    public void expire() {
        this.status = InvitationStatus.EXPIRED;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    // 가입에 사용 가능한 상태인지 판정한다.
    public boolean isUsable(LocalDateTime now) {
        return status == InvitationStatus.ISSUED && !isExpired(now);
    }
}
