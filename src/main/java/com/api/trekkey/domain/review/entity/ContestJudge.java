package com.api.trekkey.domain.review.entity;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class ContestJudge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 대회 심사위원 PK
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    // 배정된 대회
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    // 연결 사용자 — 외부 심사위원은 null (erd-mvp: 링크 초대 대상)
    private User user;

    @Column(nullable = false, length = 50)
    // 심사위원 이름 스냅샷
    private String name;

    @Column(name = "role_label", nullable = false, length = 50)
    // 심사위원 역할명. 예: 외부 심사위원, 전임교원
    private String roleLabel;

    @Column(name = "review_token_hash", nullable = false, unique = true, length = 64)
    // 심사 링크 토큰 SHA-256 — 원문은 발급 응답에만 노출한다
    private String reviewTokenHash;

    @Column(name = "token_expires_at", nullable = false)
    // 심사 링크 만료 시각
    private LocalDateTime tokenExpiresAt;

    // 링크 유출 대응 — 토큰을 새 해시로 교체하고 만료를 연장한다
    public void rotateToken(String newTokenHash, LocalDateTime newExpiresAt) {
        this.reviewTokenHash = newTokenHash;
        this.tokenExpiresAt = newExpiresAt;
    }

    public boolean isTokenExpired(LocalDateTime now) {
        return now.isAfter(tokenExpiresAt);
    }
}
