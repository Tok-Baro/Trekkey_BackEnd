package com.api.trekkey.domain.evidence.repository;

import com.api.trekkey.domain.evidence.entity.VerificationCase;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationCaseRepository extends JpaRepository<VerificationCase, Long> {
    Optional<VerificationCase> findBySubmissionId(Long submissionId);
    Optional<VerificationCase> findByPublicIdAndSubmissionOrganizationId(String publicId, Long organizationId);
    List<VerificationCase> findAllBySubmissionOrganizationIdOrderByOpenedAtAsc(Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VerificationCase c join fetch c.submission s where c.publicId = :publicId and s.organization.id = :organizationId")
    Optional<VerificationCase> findByPublicIdAndOrganizationIdForUpdate(
            @Param("publicId") String publicId, @Param("organizationId") Long organizationId);
}
