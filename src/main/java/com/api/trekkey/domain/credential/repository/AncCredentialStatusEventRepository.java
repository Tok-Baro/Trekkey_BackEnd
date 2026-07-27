package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncCredentialStatusEvent;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AncCredentialStatusEventRepository extends JpaRepository<AncCredentialStatusEvent, Long> {

    Optional<AncCredentialStatusEvent> findByIdempotencyKey(String idempotencyKey);

    boolean existsByCredentialId(Long credentialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AncCredentialStatusEvent e where e.id = :id")
    Optional<AncCredentialStatusEvent> findByIdForUpdate(@Param("id") Long id);
}
