package com.api.trekkey.domain.graduation.service;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.entity.StudentCourseRecord;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.graduation.repository.StudentCourseRecordRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TranscriptImportServiceImplTest {
    @Mock StudentAcademicProfileRepository profileRepository;
    @Mock StudentCourseRecordRepository courseRepository;
    @Mock HansungTranscriptPdfParser hansungTranscriptPdfParser;
    TranscriptImportServiceImpl service;
    StudentAcademicProfile profile;

    @BeforeEach
    void setUp() {
        service = new TranscriptImportServiceImpl(profileRepository, courseRepository, hansungTranscriptPdfParser);
        profile = StudentAcademicProfile.builder().admissionYear((short) 2025).curriculumYear((short) 2025)
                .admissionType(AdmissionType.FRESHMAN).graduationPath(GraduationPath.REGULAR)
                .majorPlanType(MajorPlanType.INTENSIVE).registeredSemesters((short) 2)
                .totalCredits(BigDecimal.ZERO).hansungCredits(BigDecimal.ZERO).transferRecognizedCredits(BigDecimal.ZERO)
                .cumulativeGpa(BigDecimal.ZERO).gpaScale(new BigDecimal("4.5")).activityPoints(0)
                .inputMode(InputMode.SUMMARY_ONLY).recordCompleteness(RecordCompleteness.PARTIAL)
                .failHistoryStatus(FailHistoryStatus.UNKNOWN).build();
        ReflectionTestUtils.setField(profile, "id", 100L);
        given(profileRepository.findByUserId(10L)).willReturn(Optional.of(profile));
    }

    @Test
    void previewsHansungCompatibleCsvWithoutChangingStoredRecords() {
        var result = service.importTranscript(10L, csv(), false);

        assertThat(result.courseCount()).isEqualTo(2);
        assertThat(result.detectedTotalCredits()).isEqualByComparingTo("3.0");
        assertThat(result.courses()).extracting(course -> course.category())
                .containsExactly("MAJOR_REQUIRED", "GENERAL_ELECTIVE");
    }

    @Test
    void replacesCoursesAndUpdatesProfileWhenConfirmed() {
        service.importTranscript(10L, csv(), true);

        verify(courseRepository).deleteAllByProfileId(100L);
        verify(courseRepository, times(2)).save(any(StudentCourseRecord.class));
        verify(profileRepository).saveAndFlush(profile);
        assertThat(profile.getInputMode()).isEqualTo(InputMode.COURSE_DETAIL);
        assertThat(profile.getFailHistoryStatus()).isEqualTo(FailHistoryStatus.EXISTS);
    }

    private MockMultipartFile csv() {
        String csv = "학기,학수번호,교과목명,학점,성적,이수구분\n"
                + "2025-1,A100,인공지능 캡스톤디자인,3.0,A+,전필\n"
                + "2025-1,B100,실험과목,3.0,F,교선\n";
        return new MockMultipartFile("file", "transcript.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
