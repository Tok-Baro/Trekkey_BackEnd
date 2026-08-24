package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.GraduationEvaluation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationEvaluationRepository extends JpaRepository<GraduationEvaluation, Long> {
    Optional<GraduationEvaluation> findFirstByProfileUserIdOrderByEvaluatedAtDesc(Long userId);
    Optional<GraduationEvaluation> findByPublicIdAndProfileUserId(String publicId, Long userId);
}
