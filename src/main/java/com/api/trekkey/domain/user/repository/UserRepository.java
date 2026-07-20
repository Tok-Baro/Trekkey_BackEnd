package com.api.trekkey.domain.user.repository;

import com.api.trekkey.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 회원가입 시 같은 이메일로 이미 가입한 사용자가 있는지 확인한다.
    boolean existsByEmail(String email);

    // 로그인 시 이메일로 사용자를 찾고, 비밀번호 검증은 AuthService에서 BCrypt로 처리한다.
    Optional<User> findByEmail(String email);
}
