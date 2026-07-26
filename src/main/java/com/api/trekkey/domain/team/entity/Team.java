package com.api.trekkey.domain.team.entity;

import com.api.trekkey.domain.contest.entity.Contest;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
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
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_contest_leader",
                columnNames = {"contest_id", "leader_user_id"}))
public class Team extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 팀/참가 신청 PK
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    // 공개 URL과 API에서 사용할 불변 식별자
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    // 참가 신청한 대회
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_user_id", nullable = false)
    // 대표 참가자 사용자
    private User leaderUser;

    @Column(nullable = false, length = 100)
    // 팀명 또는 개인전 참가자명
    private String name;

    @Column(nullable = false, length = 100)
    // 신청 당시 대표자 이름 스냅샷
    private String leaderName;

    @Column(nullable = false, length = 100)
    // 신청 당시 대표자 소속 또는 전공 스냅샷
    private String major;

    @Column(nullable = false)
    // 현재 프론트에서 입력하는 참가 인원 수
    private int memberCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    // 참가 신청 검토 상태
    private TeamStatus status;

    @Column(name = "contact_email", nullable = false, length = 255)
    // 신청 관련 연락 이메일
    private String contactEmail;

    @Column(nullable = false, length = 30)
    // 신청 관련 연락처
    private String phone;

    @Column(nullable = false, columnDefinition = "TEXT")
    // 참가 지원 동기
    private String motivation;

    // 팀원 명단 확정 시각. 확정 이후 신청 수정을 거부한다 (null이면 미확정, erd-mvp §5)
    private LocalDateTime participationFinalizedAt;

    @PrePersist
    private void assignPublicId() {
        if (publicId == null || publicId.isBlank()) {
            publicId = UUID.randomUUID().toString();
        }
    }

    // 참가자 신청 수정 — 보완요청 상태였다면 검토중으로 자동 전환한다.
    public void updateApplication(
            String name, String leaderName, String major, int memberCount,
            String contactEmail, String phone, String motivation) {
        this.name = name;
        this.leaderName = leaderName;
        this.major = major;
        this.memberCount = memberCount;
        this.contactEmail = contactEmail;
        this.phone = phone;
        this.motivation = motivation;
        if (this.status == TeamStatus.REVISION_REQUESTED) {
            this.status = TeamStatus.PENDING;
        }
    }

    // 관리자 신청 상태 변경
    public void changeStatus(TeamStatus status) {
        this.status = status;
    }

    // 팀원 명단 확정 — 이후 신청 변경 잠금 (erd-mvp §5)
    public void finalizeParticipation(LocalDateTime now) {
        this.participationFinalizedAt = now;
    }

    public boolean isFinalized() {
        return participationFinalizedAt != null;
    }
}
