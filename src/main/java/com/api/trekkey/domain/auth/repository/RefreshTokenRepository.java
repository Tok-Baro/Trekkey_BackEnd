package com.api.trekkey.domain.auth.repository;

import com.api.trekkey.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // /refresh에서는 폐기된 토큰도 조회해야 재사용 공격을 감지할 수 있다.
    // PESSIMISTIC_WRITE는 같은 refresh token으로 동시에 재발급하지 못하게 DB row를 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 같은 로그인 세션에서 파생된 refresh token을 모두 폐기한다.
    // 예전 토큰 재사용이 감지되면 현재 살아있는 토큰까지 함께 끊기 위한 용도다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken rt
            set rt.revoked = true
            where rt.familyId = :familyId
            """)
    int revokeAllByFamilyId(String familyId);
}
