package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.GraduationRequirement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationRequirementRepository extends JpaRepository<GraduationRequirement, Long> {
    List<GraduationRequirement> findAllByPolicyIdOrderBySequenceNo(Long policyId);
}
