package com.api.trekkey.domain.graduation.web.dto;

import com.api.trekkey.domain.graduation.entity.AcademicUnit;

public record AcademicUnitRes(
        String publicId,
        String externalCode,
        String name,
        String unitType,
        String parentPublicId) {

    public static AcademicUnitRes from(AcademicUnit unit) {
        return new AcademicUnitRes(
                unit.getPublicId(),
                unit.getExternalCode(),
                unit.getName(),
                unit.getUnitType().name(),
                unit.getParent() == null ? null : unit.getParent().getPublicId());
    }
}
