package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import com.api.trekkey.domain.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "student_academic_profile")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentAcademicProfile extends GraduationPublicEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "admission_year", nullable = false)
    private short admissionYear;

    @Column(name = "curriculum_year", nullable = false)
    private short curriculumYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "admission_type", nullable = false, length = 30)
    private AdmissionType admissionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "graduation_path", nullable = false, length = 40)
    private GraduationPath graduationPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "major_plan_type", nullable = false, length = 50)
    private MajorPlanType majorPlanType;

    @Column(name = "registered_semesters", nullable = false)
    private short registeredSemesters;

    @Column(name = "total_credits", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalCredits;

    @Column(name = "hansung_credits", nullable = false, precision = 5, scale = 1)
    private BigDecimal hansungCredits;

    @Column(name = "transfer_recognized_credits", nullable = false, precision = 5, scale = 1)
    private BigDecimal transferRecognizedCredits;

    @Column(name = "cumulative_gpa", nullable = false, precision = 3, scale = 2)
    private BigDecimal cumulativeGpa;

    @Column(name = "gpa_scale", nullable = false, precision = 2, scale = 1)
    private BigDecimal gpaScale;

    @Column(name = "activity_points", nullable = false)
    private int activityPoints;

    @Column(name = "international_student", nullable = false)
    private boolean internationalStudent;

    @Column(name = "teaching_program", nullable = false)
    private boolean teachingProgram;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_mode", nullable = false, length = 30)
    private InputMode inputMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_completeness", nullable = false, length = 20)
    private RecordCompleteness recordCompleteness;

    @Column(name = "summary_as_of_term", length = 6)
    private String summaryAsOfTerm;

    @Column(name = "expected_graduation_year")
    private Short expectedGraduationYear;

    @Column(name = "expected_graduation_month")
    private Short expectedGraduationMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_history_status", nullable = false, length = 20)
    private FailHistoryStatus failHistoryStatus;

    @Version
    @Column(nullable = false)
    private Long version;

    public void update(
            short admissionYear,
            short curriculumYear,
            AdmissionType admissionType,
            GraduationPath graduationPath,
            MajorPlanType majorPlanType,
            short registeredSemesters,
            BigDecimal totalCredits,
            BigDecimal hansungCredits,
            BigDecimal transferRecognizedCredits,
            BigDecimal cumulativeGpa,
            BigDecimal gpaScale,
            int activityPoints,
            boolean internationalStudent,
            boolean teachingProgram,
            InputMode inputMode,
            RecordCompleteness recordCompleteness,
            String summaryAsOfTerm,
            Short expectedGraduationYear,
            Short expectedGraduationMonth,
            FailHistoryStatus failHistoryStatus) {
        this.admissionYear = admissionYear;
        this.curriculumYear = curriculumYear;
        this.admissionType = admissionType;
        this.graduationPath = graduationPath;
        this.majorPlanType = majorPlanType;
        this.registeredSemesters = registeredSemesters;
        this.totalCredits = totalCredits;
        this.hansungCredits = hansungCredits;
        this.transferRecognizedCredits = transferRecognizedCredits;
        this.cumulativeGpa = cumulativeGpa;
        this.gpaScale = gpaScale;
        this.activityPoints = activityPoints;
        this.internationalStudent = internationalStudent;
        this.teachingProgram = teachingProgram;
        this.inputMode = inputMode;
        this.recordCompleteness = recordCompleteness;
        this.summaryAsOfTerm = summaryAsOfTerm;
        this.expectedGraduationYear = expectedGraduationYear;
        this.expectedGraduationMonth = expectedGraduationMonth;
        this.failHistoryStatus = failHistoryStatus;
    }

    public void applyImportedTranscript(
            BigDecimal importedCredits,
            BigDecimal importedGpa,
            String asOfTerm,
            boolean hasFailGrade) {
        if (importedCredits != null) {
            this.totalCredits = importedCredits;
            this.hansungCredits = importedCredits;
        }
        if (importedGpa != null) {
            this.cumulativeGpa = importedGpa;
        }
        this.inputMode = InputMode.COURSE_DETAIL;
        this.recordCompleteness = RecordCompleteness.PARTIAL;
        this.summaryAsOfTerm = asOfTerm;
        this.failHistoryStatus = hasFailGrade ? FailHistoryStatus.EXISTS : FailHistoryStatus.NONE;
    }

    public void applyImportedActivityPoints(int importedActivityPoints) {
        this.activityPoints = Math.max(0, importedActivityPoints);
    }
}
