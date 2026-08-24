package com.api.trekkey.domain.graduation.service;

import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.repository.CredentialHistoryRow;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.NonCourseRecordType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.VerificationStatus;
import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.entity.StudentNonCourseRecord;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.graduation.repository.StudentNonCourseRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrekkeyGraduationEvidenceSyncService {
    private final StudentAcademicProfileRepository profileRepository;
    private final StudentNonCourseRecordRepository nonCourseRepository;
    private final AncCredentialSubjectRepository credentialSubjectRepository;

    @Transactional
    public void sync(Long userId) {
        StudentAcademicProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) return;
        for (CredentialHistoryRow row : credentialSubjectRepository.findHistoryRowsByUserId(userId)) {
            if (row.getCredentialPublicId() == null || "REVOKED".equals(row.getStatus())) continue;
            NonCourseRecordType recordType = recordType(row.getCredentialType());
            if (recordType == null) continue;
            String evidenceType = "TREKKEY_" + row.getCredentialType();
            if (nonCourseRepository.existsByProfileIdAndExternalEvidenceTypeAndExternalIssuerCode(
                    profile.getId(), evidenceType, row.getCredentialPublicId())) continue;
            nonCourseRepository.save(StudentNonCourseRecord.builder()
                    .profile(profile)
                    .recordType(recordType)
                    .title(row.getContestTitle() == null ? "Trekkey 대회 활동" : row.getContestTitle())
                    .issuedAt(row.getIssuedAt() == null ? null : row.getIssuedAt().toLocalDate())
                    .verificationStatus(VerificationStatus.UNIVERSITY_VERIFIED)
                    .verificationAssuranceLevel("L3")
                    .externalEvidenceType(evidenceType)
                    .externalIssuerCode(row.getCredentialPublicId())
                    .note("Trekkey 발급 증명서 " + row.getCredentialNo())
                    .build());
        }
    }

    private NonCourseRecordType recordType(String credentialType) {
        if ("PARTICIPATION".equals(credentialType)) return NonCourseRecordType.CONTEST_PARTICIPATION;
        if ("AWARD".equals(credentialType)) return NonCourseRecordType.CONTEST_AWARD;
        return null;
    }
}
