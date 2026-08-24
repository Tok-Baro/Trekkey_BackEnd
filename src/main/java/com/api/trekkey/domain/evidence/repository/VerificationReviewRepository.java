package com.api.trekkey.domain.evidence.repository;

import com.api.trekkey.domain.evidence.entity.VerificationReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationReviewRepository extends JpaRepository<VerificationReview, Long> {
    List<VerificationReview> findAllByVerificationCaseIdOrderById(Long caseId);
    boolean existsByVerificationCaseIdAndReviewerId(Long caseId, Long reviewerId);
}
