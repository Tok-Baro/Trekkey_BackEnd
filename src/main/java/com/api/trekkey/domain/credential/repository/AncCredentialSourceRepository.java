package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncCredentialSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AncCredentialSourceRepository extends JpaRepository<AncCredentialSource, Long> {

    Optional<AncCredentialSource> findBySourceFingerprint(byte[] sourceFingerprint);
}
