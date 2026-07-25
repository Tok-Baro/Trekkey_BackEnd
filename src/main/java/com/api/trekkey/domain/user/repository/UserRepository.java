package com.api.trekkey.domain.user.repository;

import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 회원가입 시 같은 이메일로 이미 가입한 사용자가 있는지 확인한다.
    boolean existsByEmail(String email);

    boolean existsByOrganizationIdAndStudentId(Long organizationId, String studentId);

    // 로그인 시 이메일로 사용자를 찾고, 비밀번호 검증은 AuthService에서 BCrypt로 처리한다.
    Optional<User> findByEmail(String email);

    // 조직 내 특정 역할·상태의 사용자 목록 (예: 승인 대기 중인 ADMIN 조회)
    List<User> findByOrganizationIdAndRoleAndStatus(Long organizationId, UserRole role, UserStatus status);
}
