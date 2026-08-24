package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAcademicProfileRepository extends JpaRepository<StudentAcademicProfile, Long> {
    Optional<StudentAcademicProfile> findByUserId(Long userId);
    Optional<StudentAcademicProfile> findByPublicIdAndUserId(String publicId, Long userId);
    Optional<StudentAcademicProfile> findByPublicIdAndUserOrganizationId(String publicId, Long organizationId);

    @Query("select p.version from StudentAcademicProfile p where p.id = :id")
    Optional<Long> findVersionById(@Param("id") Long id);
}
