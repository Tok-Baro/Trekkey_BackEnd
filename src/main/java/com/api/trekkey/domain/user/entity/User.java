package com.api.trekkey.domain.user.entity;

import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "user",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_organization_student_id",
                columnNames = {"organization_id", "student_id"})) //학교 안에서 학번 유일 (erd-mvp §4)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    Organization organization;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberType memberType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    private String studentId; //학번

    private String major; //전공

    private String department; //소속부서(교직원용)

    private String position; //직책(교직원용)

    @Builder.Default
    @Column(nullable = false)
    private int failedLoginCount = 0; //로그인 연속 실패 횟수

    private LocalDateTime lockedUntil; //로그인 잠금 해제 시각 (null이면 잠금 아님)

    // 로그인 실패 횟수를 1 올리고 현재 값을 반환한다.
    public int increaseFailedLogin() {
        this.failedLoginCount += 1;
        return this.failedLoginCount;
    }

    // 로그인 성공 시 실패 카운트와 잠금을 초기화한다.
    public void resetLoginFailure() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    // 지정 시각까지 로그인 잠금
    public void lock(LocalDateTime until) {
        this.lockedUntil = until;
    }

    // 현재 잠금 상태인지 판정한다.
    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    // 관리자 가입 승인 → 활성화
    public void approve() {
        this.status = UserStatus.ACTIVE;
    }

    // 관리자 가입 거절 → 비활성화
    public void reject() {
        this.status = UserStatus.INACTIVE;
    }
}
