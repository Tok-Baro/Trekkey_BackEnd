package com.api.trekkey.domain.evidence.service;

import com.api.trekkey.domain.evidence.entity.*;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.*;
import com.api.trekkey.domain.evidence.exception.EvidenceErrorResponseCode;
import com.api.trekkey.domain.evidence.repository.*;
import com.api.trekkey.domain.evidence.support.EvidenceFileInspector;
import com.api.trekkey.domain.evidence.support.EvidenceClaimHasher;
import com.api.trekkey.domain.evidence.web.dto.*;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.NonCourseRecordType;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.submission.support.*;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceSubmissionServiceImpl implements EvidenceSubmissionService {
    private final UserRepository userRepository;
    private final StudentAcademicProfileRepository profileRepository;
    private final EvidenceSubmissionRepository submissionRepository;
    private final EvidenceFileRepository fileRepository;
    private final VerificationCaseRepository caseRepository;
    private final VerificationReviewRepository reviewRepository;
    private final FileStoragePort fileStoragePort;
    private final EvidenceFileInspector fileInspector;
    private final EvidenceClaimHasher claimHasher;

    @Override
    @Transactional
    public EvidenceSubmissionRes submit(Long userId, EvidenceSubmissionCreateReq request, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        if (!profileRepository.findByUserId(userId).isPresent()) {
            throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_PROFILE_REQUIRED);
        }
        validateRecordType(request.evidenceType(), request.targetRecordType());
        if (request.expiresAt() != null && request.issuedAt() != null
                && request.expiresAt().isBefore(request.issuedAt())) {
            throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_INVALID_DATE);
        }
        byte[] bytes = read(file);
        String detectedContentType = fileInspector.inspect(bytes);
        String credential = normalize(request.credentialNumber());
        LocalDateTime now = LocalDateTime.now();

        EvidenceSubmission submission = submissionRepository.save(EvidenceSubmission.builder()
                .organization(user.getOrganization()).submittedBy(user)
                .evidenceType(request.evidenceType()).targetRecordType(request.targetRecordType())
                .status(EvidenceStatus.UNDER_REVIEW).title(request.title().trim())
                .issuerName(request.issuerName().trim()).issuerCode(trimToNull(request.issuerCode()))
                .credentialNumberHash(credential == null ? null : claimHasher.hash(credential))
                .credentialNumberLast4(last4(credential)).numericValue(request.numericValue())
                .issuedAt(request.issuedAt()).expiresAt(request.expiresAt()).submittedAt(now).build());

        String storageKey = null;
        try {
            StoredFile stored = fileStoragePort.store(
                    "evidence/" + submission.getPublicId(), safeName(file.getOriginalFilename()),
                    new ByteArrayInputStream(bytes));
            storageKey = stored.storageKey();
            EvidenceFile evidenceFile = fileRepository.save(EvidenceFile.builder()
                    .submission(submission).originalName(safeName(file.getOriginalFilename()))
                    .contentType(detectedContentType).sizeBytes(stored.sizeBytes())
                    .storageKey(stored.storageKey()).sha256(stored.sha256())
                    .safetyStatus(FileSafetyStatus.FORMAT_VALIDATED).build());
            VerificationCase verificationCase = caseRepository.save(VerificationCase.builder()
                    .submission(submission).status(VerificationCaseStatus.MANUAL_REVIEW)
                    .requiredAssuranceLevel(AssuranceLevel.L2).openedAt(now).build());
            registerRollbackCleanup(storageKey);
            return EvidenceSubmissionRes.from(submission, verificationCase, List.of(evidenceFile), 0);
        } catch (RuntimeException exception) {
            if (storageKey != null) fileStoragePort.delete(storageKey);
            throw exception;
        }
    }

    @Override
    public List<EvidenceSubmissionRes> getMine(Long userId) {
        return submissionRepository.findAllBySubmittedByIdOrderByIdDesc(userId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    public EvidenceSubmissionRes getMine(Long userId, String publicId) {
        return toResponse(submissionRepository.findByPublicIdAndSubmittedById(publicId, userId)
                .orElseThrow(() -> new CustomException(EvidenceErrorResponseCode.EVIDENCE_NOT_FOUND)));
    }

    @Override
    public FileDownload download(Long userId, String filePublicId) {
        EvidenceFile file = fileRepository.findByPublicIdAndSubmissionSubmittedById(filePublicId, userId)
                .orElseThrow(() -> new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_NOT_FOUND));
        return new FileDownload(file.getOriginalName(), file.getContentType(), file.getSizeBytes(),
                fileStoragePort.open(file.getStorageKey()));
    }

    private EvidenceSubmissionRes toResponse(EvidenceSubmission submission) {
        VerificationCase verificationCase = caseRepository.findBySubmissionId(submission.getId())
                .orElseThrow(() -> new CustomException(EvidenceErrorResponseCode.EVIDENCE_CASE_NOT_FOUND));
        return EvidenceSubmissionRes.from(submission, verificationCase,
                fileRepository.findAllBySubmissionIdOrderById(submission.getId()),
                reviewRepository.findAllByVerificationCaseIdOrderById(verificationCase.getId()).size());
    }

    private void validateRecordType(EvidenceType type, NonCourseRecordType recordType) {
        boolean valid = switch (type) {
            case LANGUAGE_SCORE -> recordType == NonCourseRecordType.TOPIK || recordType == NonCourseRecordType.OTHER;
            case COMPLETION -> recordType == NonCourseRecordType.TEACHING_COMPLETION || recordType == NonCourseRecordType.OTHER;
            case ENROLLMENT -> recordType == NonCourseRecordType.GRADUATE_ENROLLMENT || recordType == NonCourseRecordType.OTHER;
            case CONTEST_AWARD, QUALIFICATION, EMPLOYMENT, OTHER -> recordType == NonCourseRecordType.OTHER;
        };
        if (!valid) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_RECORD_TYPE_INVALID);
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_REQUIRED);
        if (file.getSize() > EvidenceFileInspector.MAX_SIZE) throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_FILE_TOO_LARGE);
        try { return file.getBytes(); }
        catch (IOException e) { throw new CustomException(EvidenceErrorResponseCode.EVIDENCE_STORAGE_ERROR); }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "evidence";
        String normalized = name.replace('\\', '/').replace("\r", "").replace("\n", "");
        String base = normalized.substring(normalized.lastIndexOf('/') + 1);
        return base.substring(0, Math.min(base.length(), 255));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.replaceAll("[^A-Za-z0-9가-힣]", "").toUpperCase();
    }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String last4(String value) { return value == null ? null : value.substring(Math.max(0, value.length() - 4)); }
    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) fileStoragePort.delete(storageKey);
            }
        });
    }
}
