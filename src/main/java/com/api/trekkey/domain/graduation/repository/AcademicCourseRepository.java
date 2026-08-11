package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.AcademicCourse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicCourseRepository extends JpaRepository<AcademicCourse, Long> {
    Optional<AcademicCourse> findByPublicIdAndOrganizationId(String publicId, Long organizationId);
}
