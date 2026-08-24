package com.api.trekkey.domain.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.evidence.exception.EvidenceErrorResponseCode;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.EvidenceType;
import com.api.trekkey.domain.evidence.repository.*;
import com.api.trekkey.domain.evidence.support.EvidenceClaimHasher;
import com.api.trekkey.domain.evidence.support.EvidenceFileInspector;
import com.api.trekkey.domain.evidence.web.dto.EvidenceSubmissionCreateReq;
import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.NonCourseRecordType;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class EvidenceSubmissionServiceImplTest {
    @Mock UserRepository userRepository;
    @Mock StudentAcademicProfileRepository profileRepository;
    @Mock EvidenceSubmissionRepository submissionRepository;
    @Mock EvidenceFileRepository fileRepository;
    @Mock VerificationCaseRepository caseRepository;
    @Mock VerificationReviewRepository reviewRepository;
    @Mock FileStoragePort fileStoragePort;
    @Mock EvidenceClaimHasher claimHasher;
    @Mock User user;
    @Mock Organization organization;
    @Mock StudentAcademicProfile profile;
    private EvidenceSubmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EvidenceSubmissionServiceImpl(userRepository, profileRepository, submissionRepository,
                fileRepository, caseRepository, reviewRepository, fileStoragePort,
                new EvidenceFileInspector(), claimHasher);
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(10L)).willReturn(Optional.of(profile));
    }

    @Test
    void rejectsMoreThanFiveDocumentsInOneGraduationEvidenceBundle() throws Exception {
        byte[] pdf = pdf("bundle");
        List<MultipartFile> files = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> (MultipartFile) new MockMultipartFile("files", index + ".pdf", "application/pdf", pdf))
                .toList();

        assertCode(() -> service.submit(10L, request(), files),
                EvidenceErrorResponseCode.EVIDENCE_FILE_BUNDLE_TOO_LARGE.getCode());
        verifyNoInteractions(submissionRepository, fileStoragePort);
    }

    @Test
    void rejectsDuplicateDocumentsInsideOneBundle() throws Exception {
        byte[] pdf = pdf("same document");
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "first.pdf", "application/pdf", pdf),
                new MockMultipartFile("files", "second.pdf", "application/pdf", pdf));

        assertCode(() -> service.submit(10L, request(), files),
                EvidenceErrorResponseCode.EVIDENCE_FILE_DUPLICATE.getCode());
        verifyNoInteractions(submissionRepository, fileStoragePort);
    }

    private EvidenceSubmissionCreateReq request() {
        return new EvidenceSubmissionCreateReq(EvidenceType.GRADUATION_WORK, NonCourseRecordType.GRADUATION_WORK,
                "캡스톤 졸업작품", "한성대학교", null, null, null, null, null);
    }

    private byte[] pdf(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph(text));
        document.close();
        return output.toByteArray();
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable).isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getBaseResponseCode().getCode())
                        .isEqualTo(code));
    }
}
