package com.api.trekkey.domain.evidence.web.dto;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.EvidenceType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.NonCourseRecordType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EvidenceSubmissionCreateReq(
        @NotNull EvidenceType evidenceType,
        @NotNull NonCourseRecordType targetRecordType,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String issuerName,
        @Size(max = 100) String issuerCode,
        @Size(max = 200) String credentialNumber,
        @PositiveOrZero BigDecimal numericValue,
        @PastOrPresent LocalDate issuedAt,
        LocalDate expiresAt) {}
