package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ActivityImportServiceTest {
    @Mock StudentAcademicProfileRepository profileRepository;

    @Test
    void importsSmartPortalPointExport() {
        StudentAcademicProfile profile = StudentAcademicProfile.builder().admissionYear((short) 2025)
                .curriculumYear((short) 2025).admissionType(AdmissionType.FRESHMAN)
                .graduationPath(GraduationPath.REGULAR).majorPlanType(MajorPlanType.INTENSIVE)
                .registeredSemesters((short) 2).totalCredits(BigDecimal.ZERO).hansungCredits(BigDecimal.ZERO)
                .transferRecognizedCredits(BigDecimal.ZERO).cumulativeGpa(BigDecimal.ZERO)
                .gpaScale(new BigDecimal("4.5")).activityPoints(0).inputMode(InputMode.SUMMARY_ONLY)
                .recordCompleteness(RecordCompleteness.PARTIAL).failHistoryStatus(FailHistoryStatus.UNKNOWN).build();
        given(profileRepository.findByUserId(10L)).willReturn(Optional.of(profile));
        ActivityImportService service = new ActivityImportService(profileRepository);
        String csv = "취업멘토링,40,2026-06-12\n튜터링,30,2026-06-30\n합계,70점\n";

        var result = service.importActivities(10L,
                new MockMultipartFile("file", "points.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)), true);

        assertThat(result.detectedPoints()).isEqualTo(70);
        assertThat(profile.getActivityPoints()).isEqualTo(70);
        verify(profileRepository).saveAndFlush(profile);
    }
}
