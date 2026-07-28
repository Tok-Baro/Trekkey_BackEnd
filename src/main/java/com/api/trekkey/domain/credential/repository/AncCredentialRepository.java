package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AncCredentialRepository extends JpaRepository<AncCredential, Long> {

    Optional<AncCredential> findByPublicId(String publicId);

    Optional<AncCredential> findByCredentialIdHash(byte[] credentialIdHash);

    Optional<AncCredential> findByIssuerOrganizationIdAndCredentialNo(
            Long issuerOrganizationId,
            String credentialNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AncCredential c where c.status = :status order by c.createdAt asc, c.id asc")
    List<AncCredential> findBatchClaimCandidatesForUpdate(
            @Param("status") CredentialStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from AncCredential c
            where c.issuerOrganizationId = :issuerOrganizationId
              and c.schemaVersionHash = :schemaVersionHash
              and c.status = :status
            order by c.createdAt asc, c.id asc
            """)
    List<AncCredential> findBatchClaimCandidatesForUpdate(
            @Param("issuerOrganizationId") Long issuerOrganizationId,
            @Param("schemaVersionHash") byte[] schemaVersionHash,
            @Param("status") CredentialStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AncCredential c where c.publicId = :publicId")
    Optional<AncCredential> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AncCredential c where c.id = :id")
    Optional<AncCredential> findByIdForUpdate(@Param("id") Long id);
}
