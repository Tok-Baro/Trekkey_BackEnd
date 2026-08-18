package com.api.trekkey.domain.evidence.repository;

import com.api.trekkey.domain.evidence.entity.EvidenceSubmission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceSubmissionRepository extends JpaRepository<EvidenceSubmission, Long> {
    List<EvidenceSubmission> findAllBySubmittedByIdOrderByIdDesc(Long userId);
    Optional<EvidenceSubmission> findByPublicIdAndSubmittedById(String publicId, Long userId);
    Optional<EvidenceSubmission> findByPublicIdAndOrganizationId(String publicId, Long organizationId);
}
