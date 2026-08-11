package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.StudentAcademicUnit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAcademicUnitRepository extends JpaRepository<StudentAcademicUnit, Long> {
    List<StudentAcademicUnit> findAllByProfileIdOrderBySequenceNo(Long profileId);
}
