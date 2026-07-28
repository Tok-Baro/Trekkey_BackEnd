package com.api.trekkey.domain.award.entity;

import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.team.entity.Team;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀 단위 수상 결과 — 공식 ENTRY를 근거로 발급하며 팀원들은 같은 certificateNo를 공유한다 (erd-mvp §5).
 * CONFIRMED 상태가 수상 Credential의 발급 원천이 된다 (erd-mvp §6).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Award extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 수상 결과 PK
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    // 공개 URL과 API에서 사용할 불변 식별자
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_round_entry_id", nullable = false, unique = true)
    // 수상 근거 공식 결과 — ENTRY당 한 건 (erd-mvp 제약)
    private ReviewRoundEntry reviewRoundEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    // 수상 팀 — 조회용 비정규화 FK, ENTRY에서 도달한 팀과 같아야 한다 (erd-mvp §5)
    private Team team;

    @Column(name = "award_rank_no", nullable = false)
    // 수상 순위
    private int awardRankNo;

    @Column(nullable = false, length = 50)
    // 상격. 예: 대상, 최우수상
    private String prize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    // 수상 상태
    private AwardStatus status;

    @Column(name = "certificate_no", nullable = false, unique = true, length = 40)
    // 팀 단위 상장 번호 — 팀원들이 공유한다
    private String certificateNo;

    @Column(name = "confirmed_at")
    // 수상 확정 시각
    private LocalDateTime confirmedAt;

    @PrePersist
    private void assignPublicId() {
        if (publicId == null || publicId.isBlank()) {
            publicId = UUID.randomUUID().toString();
        }
    }

    // 수상 확정 — 이후 Credential 발급 원천이 된다
    public void confirm(LocalDateTime now) {
        this.status = AwardStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    // 후보/보류 전환 (확정 전 운영 조정)
    public void changeStatus(AwardStatus status) {
        this.status = status;
    }
}
