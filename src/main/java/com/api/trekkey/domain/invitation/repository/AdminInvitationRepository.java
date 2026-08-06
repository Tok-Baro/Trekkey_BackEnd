package com.api.trekkey.domain.invitation.repository;

import com.api.trekkey.domain.invitation.entity.AdminInvitation;
import com.api.trekkey.domain.invitation.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, Long> {

    // 가입 시 초대 토큰 해시로 초대를 찾는다. (원문 미저장 — RefreshToken과 동일 패턴)
    Optional<AdminInvitation> findByTokenHash(String tokenHash);

    // 조직의 초대 목록 (최신 발급 순)
    List<AdminInvitation> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    // 동일 이메일의 ISSUED 초대 중복 발급 방지
    boolean existsByEmailAndStatus(String email, InvitationStatus status);
}
