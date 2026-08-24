package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.AcademicUnit;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.AcademicUnitStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicUnitRepository extends JpaRepository<AcademicUnit, Long> {
    Optional<AcademicUnit> findByPublicIdAndOrganizationId(String publicId, Long organizationId);
    Optional<AcademicUnit> findByOrganizationIdAndExternalCode(Long organizationId, String externalCode);
    List<AcademicUnit> findAllByOrganizationIdAndStatusOrderByName(Long organizationId, AcademicUnitStatus status);
    List<AcademicUnit> findAllByOrganizationIdOrderByUnitTypeAscNameAsc(Long organizationId);
}
