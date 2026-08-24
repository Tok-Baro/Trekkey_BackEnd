package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.StudentCourseRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCourseRecordRepository extends JpaRepository<StudentCourseRecord, Long> {
    List<StudentCourseRecord> findAllByProfileUserIdOrderByTermAscCourseNameAsc(Long userId);
    Optional<StudentCourseRecord> findByPublicIdAndProfileUserId(String publicId, Long userId);
    void deleteAllByProfileId(Long profileId);
}
