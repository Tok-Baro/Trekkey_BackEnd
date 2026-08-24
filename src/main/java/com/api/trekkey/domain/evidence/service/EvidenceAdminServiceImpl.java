package com.api.trekkey.domain.evidence.service;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.evidence.entity.*;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.*;
import com.api.trekkey.domain.evidence.exception.EvidenceErrorResponseCode;
import com.api.trekkey.domain.evidence.repository.*;
import com.api.trekkey.domain.evidence.support.EvidenceReviewConsensus;
import com.api.trekkey.domain.evidence.web.dto.*;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.VerificationStatus;
import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.entity.StudentNonCourseRecord;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.graduation.repository.StudentNonCourseRecordRepository;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceAdminServiceImpl implements EvidenceAdminService {
    private final UserRepository userRepository;
    private final EvidenceSubmissionRepository submissionRepository;
    private final EvidenceFileRepository fileRepository;
    private final VerificationCaseRepository caseRepository;
    private final VerificationReviewRepository reviewRepository;
    private final VerificationDecisionRepository decisionRepository;
    private final EvidenceBindingRepository bindingRepository;
    private final StudentAcademicProfileRepository profileRepository;
    private final StudentNonCourseRecordRepository nonCourseRepository;
    private final FileStoragePort fileStoragePort;
    private final AdminAuditLogger adminAuditLogger;

    @Override
    public List<EvidenceSubmissionRes> getQueue(Long adminId, VerificationCaseStatus status) {
        User admin = findAdmin(adminId);
        return caseRepository.findAllBySubmissionOrganizationIdOrderByOpenedAtAsc(admin.getOrganization().getId())
                .stream().filter(item -> status == null || item.getStatus() == status)
                .map(this::toResponse).toList();
    }

    @Override
    public EvidenceSubmissionRes getCase(Long adminId, String casePublicId) {
        User admin = findAdmin(adminId);
        VerificationCase verificationCase = caseRepository
                .findByPublicIdAndSubmissionOrganizationId(casePublicId, admin.getOrganization().getId())
                .orElseThrow(() -> new CustomException(EvidenceErrorResponseCode.EVIDENCE_CASE_NOT_FOUND));
        return toResponse(verificationCase);
    }

    @Override
    @Transactional
    public EvidenceReviewRes review(Long adminId, String casePublicId, EvidenceReviewReq request) {
        User admin = findAdmin(adminId);
        if (request.assuranceLevel() != AssuranceLevel.L2) {
            throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_INVALID_REVIEW);
        }
        String officialReferenceUrl = validateOfficialReference(request);
        VerificationCase verificationCase = caseRepository.findByPublicIdAndOrganizationIdForUpdate(
                        casePublicId, admin.getOrganization().getId())
                .orElseThrow(() -> new CustomException(EvidenceErrorResponseCode.EVIDENCE_CASE_NOT_FOUND));
        if (verificationCase.isClosed()) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_REVIEW_CLOSED);
        if (request.result() == ReviewResult.APPROVE
                && verificationCase.getSubmission().getExpiresAt() != null
                && verificationCase.getSubmission().getExpiresAt().isBefore(LocalDate.now())) {
            throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_EXPIRED);
        }
        if (verificationCase.getSubmission().getSubmittedBy().getId().equals(adminId)
                || reviewRepository.existsByVerificationCaseIdAndReviewerId(verificationCase.getId(), adminId)) {
            throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_REVIEWER_CONFLICT);
        }
        List<VerificationReview> previous = reviewRepository
                .findAllByVerificationCaseIdOrderById(verificationCase.getId());
        if (previous.size() >= 2) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_REVIEW_LIMIT);

        LocalDateTime now = LocalDateTime.now();
        VerificationReview review = reviewRepository.save(VerificationReview.builder()
                .verificationCase(verificationCase).reviewer(admin).result(request.result())
                .assuranceLevel(AssuranceLevel.L2).reasonCode(request.reasonCode().name())
                .officialReferenceUrl(officialReferenceUrl)
                .note(trimToNull(request.note())).reviewedAt(now).build());
        adminAuditLogger.log(adminId, admin.getOrganization().getId(), AuditAction.EVIDENCE_REVIEW,
                "EVIDENCE_VERIFICATION_CASE", verificationCase.getId(),
                "result=" + request.result() + ",reason=" + request.reasonCode().name());

        if (previous.isEmpty()) {
            verificationCase.awaitSecondReview();
            verificationCase.getSubmission().awaitSecondReview();
            return new EvidenceReviewRes(casePublicId, verificationCase.getStatus(), 1,
                    request.result(), null, AssuranceLevel.L2, now);
        }

        VerificationReview first = previous.getFirst();
        VerificationDecisionType finalDecision = EvidenceReviewConsensus.decide(first.getResult(), review.getResult());
        VerificationCaseStatus finalStatus = switch (finalDecision) {
            case VERIFIED -> VerificationCaseStatus.VERIFIED;
            case REJECTED -> VerificationCaseStatus.REJECTED;
            case INCONCLUSIVE -> VerificationCaseStatus.INCONCLUSIVE;
        };
        verificationCase.close(finalStatus, AssuranceLevel.L2, now);
        switch (finalDecision) {
            case VERIFIED -> verificationCase.getSubmission().verify();
            case REJECTED -> verificationCase.getSubmission().reject();
            case INCONCLUSIVE -> verificationCase.getSubmission().markInconclusive();
        }

        VerificationDecision decision = decisionRepository.save(VerificationDecision.builder()
                .verificationCase(verificationCase).decision(finalDecision).assuranceLevel(AssuranceLevel.L2)
                .reasonCode(consensusReason(finalDecision)).primaryReviewer(first.getReviewer())
                .secondaryReviewer(admin).decidedAt(now)
                .bundleHash(bundleHash(verificationCase, first, review, finalDecision)).build());
        if (finalDecision == VerificationDecisionType.VERIFIED) bindGraduationRecord(decision, admin, now);
        adminAuditLogger.log(adminId, admin.getOrganization().getId(), AuditAction.EVIDENCE_DECISION,
                "EVIDENCE_VERIFICATION_DECISION", decision.getId(), "decision=" + finalDecision);
        return new EvidenceReviewRes(casePublicId, finalStatus, 2, request.result(),
                finalDecision, AssuranceLevel.L2, now);
    }

    @Override
    public FileDownload download(Long adminId, String filePublicId) {
        User admin = findAdmin(adminId);
        EvidenceFile file = fileRepository.findByPublicIdAndSubmissionOrganizationId(
                        filePublicId, admin.getOrganization().getId())
                .orElseThrow(() -> new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_NOT_FOUND));
        adminAuditLogger.log(adminId, admin.getOrganization().getId(), AuditAction.EVIDENCE_FILE_DOWNLOAD,
                "EVIDENCE_FILE", file.getId(), "filePublicId=" + file.getPublicId());
        return new FileDownload(file.getOriginalName(), file.getContentType(), file.getSizeBytes(),
                fileStoragePort.open(file.getStorageKey()));
    }

    private void bindGraduationRecord(VerificationDecision decision, User verifiedBy, LocalDateTime now) {
        EvidenceSubmission submission = decision.getVerificationCase().getSubmission();
        StudentAcademicProfile profile = profileRepository.findByUserId(submission.getSubmittedBy().getId())
                .orElseThrow(() -> new CustomException(EvidenceErrorResponseCode.EVIDENCE_PROFILE_REQUIRED));
        StudentNonCourseRecord record = nonCourseRepository.save(StudentNonCourseRecord.builder()
                .profile(profile).recordType(submission.getTargetRecordType()).title(submission.getTitle())
                .numericValue(submission.getNumericValue()).issuedAt(submission.getIssuedAt())
                .expiresAt(submission.getExpiresAt()).verificationStatus(VerificationStatus.DOCUMENT_VERIFIED)
                .verificationAssuranceLevel(AssuranceLevel.L2.name()).verifiedBy(verifiedBy).verifiedAt(now)
                .externalEvidenceType(submission.getEvidenceType().name())
                .externalIssuerCode(submission.getIssuerCode())
                .note("외부 증빙 2인 관리자 검수: " + decision.getPublicId()).build());
        bindingRepository.save(EvidenceBinding.builder().decision(decision)
                .studentNonCourseRecord(record).boundAt(now).build());
    }

    private EvidenceSubmissionRes toResponse(VerificationCase verificationCase) {
        EvidenceSubmission submission = verificationCase.getSubmission();
        return EvidenceSubmissionRes.from(submission, verificationCase,
                fileRepository.findAllBySubmissionIdOrderById(submission.getId()),
                reviewRepository.findAllByVerificationCaseIdOrderById(verificationCase.getId()).size());
    }

    private User findAdmin(Long adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.ROOT_ADMIN) {
            throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_ADMIN_REQUIRED);
        }
        return user;
    }

    private String consensusReason(VerificationDecisionType decision) {
        return switch (decision) {
            case VERIFIED -> "TWO_REVIEWERS_APPROVED";
            case REJECTED -> "TWO_REVIEWERS_REJECTED";
            case INCONCLUSIVE -> "REVIEWERS_DISAGREED";
        };
    }

    private String bundleHash(VerificationCase verificationCase, VerificationReview first,
                              VerificationReview second, VerificationDecisionType decision) {
        String canonical = verificationCase.getSubmission().getPublicId() + "|"
                + first.getReviewer().getId() + "|" + first.getResult() + "|"
                + second.getReviewer().getId() + "|" + second.getResult() + "|" + decision;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String validateOfficialReference(EvidenceReviewReq request) {
        String value = trimToNull(request.officialReferenceUrl());
        if (request.result() != ReviewResult.APPROVE && value == null) return null;
        if (value == null) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_INVALID_REFERENCE);
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_INVALID_REFERENCE);
            }
            return uri.normalize().toString();
        } catch (URISyntaxException exception) {
            throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_INVALID_REFERENCE);
        }
    }
}
