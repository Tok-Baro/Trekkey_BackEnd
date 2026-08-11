package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.GraduationPolicy;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.PolicyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationPolicyRepository extends JpaRepository<GraduationPolicy, Long> {
    Optional<GraduationPolicy> findByPublicIdAndOrganizationId(String publicId, Long organizationId);
    List<GraduationPolicy> findAllByOrganizationIdAndStatus(Long organizationId, PolicyStatus status);
}
