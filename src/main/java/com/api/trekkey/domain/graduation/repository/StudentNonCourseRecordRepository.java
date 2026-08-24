package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.StudentNonCourseRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentNonCourseRecordRepository extends JpaRepository<StudentNonCourseRecord, Long> {
    List<StudentNonCourseRecord> findAllByProfileUserIdOrderById(Long userId);
    Optional<StudentNonCourseRecord> findByPublicIdAndProfileUserId(String publicId, Long userId);
    Optional<StudentNonCourseRecord> findByPublicIdAndProfileUserOrganizationId(String publicId, Long organizationId);
    boolean existsByProfileIdAndExternalEvidenceTypeAndExternalIssuerCode(
            Long profileId, String externalEvidenceType, String externalIssuerCode);
}
