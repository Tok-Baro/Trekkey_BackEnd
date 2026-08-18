package com.api.trekkey.domain.evidence.web.dto;

import com.api.trekkey.domain.evidence.entity.*;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.*;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.NonCourseRecordType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EvidenceSubmissionRes(
        String publicId,
        String subjectName,
        String studentId,
        EvidenceType evidenceType,
        NonCourseRecordType targetRecordType,
        String title,
        String issuerName,
        String issuerCode,
        String credentialNumberMasked,
        BigDecimal numericValue,
        LocalDate issuedAt,
        LocalDate expiresAt,
        EvidenceStatus status,
        String casePublicId,
        VerificationCaseStatus caseStatus,
        AssuranceLevel achievedAssuranceLevel,
        int reviewCount,
        List<FileRes> files,
        LocalDateTime submittedAt) {

    public static EvidenceSubmissionRes from(
            EvidenceSubmission submission, VerificationCase verificationCase,
            List<EvidenceFile> files, int reviewCount) {
        String last4 = submission.getCredentialNumberLast4();
        return new EvidenceSubmissionRes(
                submission.getPublicId(), submission.getSubmittedBy().getName(),
                submission.getSubmittedBy().getStudentId(), submission.getEvidenceType(), submission.getTargetRecordType(),
                submission.getTitle(), submission.getIssuerName(), submission.getIssuerCode(),
                last4 == null ? null : "****" + last4, submission.getNumericValue(),
                submission.getIssuedAt(), submission.getExpiresAt(), submission.getStatus(),
                verificationCase.getPublicId(), verificationCase.getStatus(),
                verificationCase.getAchievedAssuranceLevel(), reviewCount,
                files.stream().map(FileRes::from).toList(), submission.getSubmittedAt());
    }

    public record FileRes(
            String publicId, String originalName, String contentType, long sizeBytes,
            String sha256, FileSafetyStatus safetyStatus) {
        static FileRes from(EvidenceFile file) {
            return new FileRes(file.getPublicId(), file.getOriginalName(), file.getContentType(),
                    file.getSizeBytes(), file.getSha256(), file.getSafetyStatus());
        }
    }
}
