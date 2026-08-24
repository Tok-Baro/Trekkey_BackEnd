package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.GraduationPolicySource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationPolicySourceRepository extends JpaRepository<GraduationPolicySource, Long> {
    List<GraduationPolicySource> findAllByPolicyIdOrderById(Long policyId);
}
