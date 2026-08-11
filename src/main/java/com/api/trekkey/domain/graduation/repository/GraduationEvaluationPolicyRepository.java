package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.GraduationEvaluationPolicy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationEvaluationPolicyRepository extends JpaRepository<GraduationEvaluationPolicy, Long> {
    List<GraduationEvaluationPolicy> findAllByEvaluationIdOrderById(Long evaluationId);
}
