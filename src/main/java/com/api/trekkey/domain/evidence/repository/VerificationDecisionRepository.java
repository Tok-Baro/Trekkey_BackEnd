package com.api.trekkey.domain.evidence.repository;

import com.api.trekkey.domain.evidence.entity.VerificationDecision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationDecisionRepository extends JpaRepository<VerificationDecision, Long> {
    Optional<VerificationDecision> findByVerificationCaseId(Long caseId);
}
