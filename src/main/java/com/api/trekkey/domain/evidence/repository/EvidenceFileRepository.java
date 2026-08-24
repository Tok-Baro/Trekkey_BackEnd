package com.api.trekkey.domain.evidence.repository;

import com.api.trekkey.domain.evidence.entity.EvidenceFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, Long> {
    List<EvidenceFile> findAllBySubmissionIdOrderById(Long submissionId);
    Optional<EvidenceFile> findByPublicIdAndSubmissionSubmittedById(String publicId, Long userId);
    Optional<EvidenceFile> findByPublicIdAndSubmissionOrganizationId(String publicId, Long organizationId);
}
