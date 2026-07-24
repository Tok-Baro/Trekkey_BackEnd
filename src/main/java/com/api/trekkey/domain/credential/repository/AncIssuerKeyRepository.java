package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncIssuerKey;
import com.api.trekkey.domain.credential.entity.IssuerKeyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AncIssuerKeyRepository extends JpaRepository<AncIssuerKey, Long> {

    Optional<AncIssuerKey> findByOrganizationIdAndKeyVersion(Long organizationId, int keyVersion);

    List<AncIssuerKey> findAllByOrganizationIdAndStatusOrderByKeyVersionDesc(
            Long organizationId,
            IssuerKeyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from AncIssuerKey k where k.organizationId = :organizationId and k.status = :status")
    Optional<AncIssuerKey> findByOrganizationIdAndStatusForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("status") IssuerKeyStatus status);
}
