package com.api.trekkey.domain.evidence.service;

import static com.api.trekkey.domain.evidence.entity.EvidenceTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.evidence.entity.*;
import com.api.trekkey.domain.evidence.repository.*;
import com.api.trekkey.domain.evidence.web.dto.*;
import com.api.trekkey.domain.graduation.entity.*;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import com.api.trekkey.domain.graduation.repository.*;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.domain.user.entity.*;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EvidenceAdminServiceImplTest {
    @Mock UserRepository userRepository;
    @Mock EvidenceSubmissionRepository submissionRepository;
    @Mock EvidenceFileRepository fileRepository;
    @Mock VerificationCaseRepository caseRepository;
    @Mock VerificationReviewRepository reviewRepository;
    @Mock VerificationDecisionRepository decisionRepository;
    @Mock EvidenceBindingRepository bindingRepository;
    @Mock StudentAcademicProfileRepository profileRepository;
    @Mock StudentNonCourseRecordRepository nonCourseRepository;
    @Mock FileStoragePort fileStoragePort;
    @Mock AdminAuditLogger auditLogger;

    EvidenceAdminServiceImpl service;
    Organization organization;
    User student;
    User admin1;
    User admin2;
    EvidenceSubmission submission;
    VerificationCase verificationCase;
    StudentAcademicProfile profile;

    @BeforeEach
    void setUp() {
        service = new EvidenceAdminServiceImpl(userRepository, submissionRepository, fileRepository,
                caseRepository, reviewRepository, decisionRepository, bindingRepository, profileRepository,
                nonCourseRepository, fileStoragePort, auditLogger);
        organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "id", 1L);
        student = user(10L, UserRole.PARTICIPANT, "student@test.com");
        admin1 = user(20L, UserRole.ADMIN, "admin1@test.com");
        admin2 = user(21L, UserRole.ADMIN, "admin2@test.com");
        submission = EvidenceSubmission.builder().organization(organization).submittedBy(student)
                .evidenceType(EvidenceType.QUALIFICATION).targetRecordType(NonCourseRecordType.OTHER)
                .status(EvidenceTypes.EvidenceStatus.UNDER_REVIEW).title("정보처리기사").issuerName("한국산업인력공단")
                .numericValue(BigDecimal.ONE).submittedAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(submission, "id", 100L);
        ReflectionTestUtils.setField(submission, "publicId", "submission-public-id");
        verificationCase = VerificationCase.builder().submission(submission)
                .status(VerificationCaseStatus.MANUAL_REVIEW).requiredAssuranceLevel(AssuranceLevel.L2)
                .openedAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(verificationCase, "id", 200L);
        ReflectionTestUtils.setField(verificationCase, "publicId", "case-public-id");
        profile = StudentAcademicProfile.builder().user(student).admissionYear((short) 2021)
                .curriculumYear((short) 2021).admissionType(AdmissionType.FRESHMAN)
                .graduationPath(GraduationPath.REGULAR).majorPlanType(MajorPlanType.CONVERGENCE_I)
                .registeredSemesters((short) 8).totalCredits(BigDecimal.ZERO).hansungCredits(BigDecimal.ZERO)
                .transferRecognizedCredits(BigDecimal.ZERO).cumulativeGpa(BigDecimal.ZERO)
                .gpaScale(new BigDecimal("4.5")).inputMode(InputMode.SUMMARY_ONLY)
                .recordCompleteness(RecordCompleteness.PARTIAL).failHistoryStatus(FailHistoryStatus.UNKNOWN).build();
        ReflectionTestUtils.setField(profile, "id", 300L);
    }

    @Test
    void firstReviewCannotVerifyAndWaitsForDifferentAdmin() {
        given(userRepository.findById(20L)).willReturn(Optional.of(admin1));
        given(caseRepository.findByPublicIdAndOrganizationIdForUpdate("case-public-id", 1L))
                .willReturn(Optional.of(verificationCase));
        given(reviewRepository.findAllByVerificationCaseIdOrderById(200L)).willReturn(List.of());
        given(reviewRepository.save(any())).willAnswer(call -> call.getArgument(0));

        EvidenceReviewRes result = service.review(20L, "case-public-id", request(ReviewResult.APPROVE));

        assertThat(result.caseStatus()).isEqualTo(VerificationCaseStatus.AWAITING_SECOND_REVIEW);
        assertThat(submission.getStatus()).isEqualTo(EvidenceTypes.EvidenceStatus.AWAITING_SECOND_REVIEW);
        verifyNoInteractions(decisionRepository, nonCourseRepository, bindingRepository);
    }

    @Test
    void twoDifferentApprovalsCreateOneVerifiedGraduationRecord() {
        VerificationReview first = review(admin1, ReviewResult.APPROVE);
        given(userRepository.findById(21L)).willReturn(Optional.of(admin2));
        given(caseRepository.findByPublicIdAndOrganizationIdForUpdate("case-public-id", 1L))
                .willReturn(Optional.of(verificationCase));
        given(reviewRepository.findAllByVerificationCaseIdOrderById(200L)).willReturn(List.of(first));
        given(reviewRepository.save(any())).willAnswer(call -> call.getArgument(0));
        given(decisionRepository.save(any())).willAnswer(call -> {
            VerificationDecision decision = call.getArgument(0);
            ReflectionTestUtils.setField(decision, "id", 400L);
            ReflectionTestUtils.setField(decision, "publicId", "decision-public-id");
            return decision;
        });
        given(profileRepository.findByUserId(10L)).willReturn(Optional.of(profile));
        given(nonCourseRepository.save(any())).willAnswer(call -> call.getArgument(0));
        given(bindingRepository.save(any())).willAnswer(call -> call.getArgument(0));

        EvidenceReviewRes result = service.review(21L, "case-public-id", request(ReviewResult.APPROVE));

        assertThat(result.finalDecision()).isEqualTo(VerificationDecisionType.VERIFIED);
        assertThat(submission.getStatus()).isEqualTo(EvidenceTypes.EvidenceStatus.VERIFIED);
        verify(nonCourseRepository, times(1)).save(argThat(record ->
                record.getVerificationStatus() == VerificationStatus.DOCUMENT_VERIFIED
                        && "L2".equals(record.getVerificationAssuranceLevel())));
        verify(bindingRepository, times(1)).save(any());
    }

    @Test
    void sameAdminCannotReviewTwice() {
        given(userRepository.findById(20L)).willReturn(Optional.of(admin1));
        given(caseRepository.findByPublicIdAndOrganizationIdForUpdate("case-public-id", 1L))
                .willReturn(Optional.of(verificationCase));
        given(reviewRepository.existsByVerificationCaseIdAndReviewerId(200L, 20L)).willReturn(true);

        assertThatThrownBy(() -> service.review(20L, "case-public-id", request(ReviewResult.APPROVE)))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode().getCode())
                        .isEqualTo("EVIDENCE_REVIEWER_CONFLICT"));
        verifyNoInteractions(decisionRepository, nonCourseRepository, bindingRepository);
    }

    @Test
    void approvalRequiresHttpsOfficialReference() {
        given(userRepository.findById(20L)).willReturn(Optional.of(admin1));
        EvidenceReviewReq request = new EvidenceReviewReq(ReviewResult.APPROVE, AssuranceLevel.L2,
                ReviewReasonCode.OFFICIAL_SOURCE_MATCH, "http://localhost/fake", "확인");

        assertThatThrownBy(() -> service.review(20L, "case-public-id", request))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode().getCode())
                        .isEqualTo("EVIDENCE_INVALID_REFERENCE"));
        verifyNoInteractions(caseRepository, decisionRepository, nonCourseRepository, bindingRepository);
    }

    private User user(long id, UserRole role, String email) {
        User user = User.builder().organization(organization).name(email).email(email).password("encoded")
                .role(role).memberType(role == UserRole.PARTICIPANT ? MemberType.STUDENT : MemberType.STAFF)
                .status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private VerificationReview review(User reviewer, ReviewResult result) {
        VerificationReview review = VerificationReview.builder().verificationCase(verificationCase).reviewer(reviewer)
                .result(result).assuranceLevel(AssuranceLevel.L2).reasonCode("OFFICIAL_SOURCE_MATCH")
                .reviewedAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(review, "id", 500L);
        return review;
    }

    private EvidenceReviewReq request(ReviewResult result) {
        return new EvidenceReviewReq(result, AssuranceLevel.L2, ReviewReasonCode.OFFICIAL_SOURCE_MATCH,
                "https://c.q-net.or.kr/authentic/lcsAuthen.do", "확인");
    }
}
