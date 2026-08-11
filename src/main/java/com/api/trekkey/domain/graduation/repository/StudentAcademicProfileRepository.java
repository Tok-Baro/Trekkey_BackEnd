package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAcademicProfileRepository extends JpaRepository<StudentAcademicProfile, Long> {
    Optional<StudentAcademicProfile> findByUserId(Long userId);
    Optional<StudentAcademicProfile> findByPublicIdAndUserId(String publicId, Long userId);
    Optional<StudentAcademicProfile> findByPublicIdAndUserOrganizationId(String publicId, Long organizationId);
}
