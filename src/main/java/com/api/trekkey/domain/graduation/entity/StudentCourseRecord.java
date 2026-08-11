package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "student_course_record", uniqueConstraints = @UniqueConstraint(
        name = "uk_student_course_term_code", columnNames = {"profile_id", "term", "course_code"}),
        indexes = {
                @Index(name = "idx_student_course_name_term", columnList = "profile_id,course_name,term"),
                @Index(name = "idx_student_course_evaluation", columnList = "profile_id,category,academic_unit_id")
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentCourseRecord extends GraduationPublicEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private StudentAcademicProfile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_course_id")
    private AcademicCourse academicCourse;

    @Column(nullable = false, length = 6)
    private String term;

    @Column(name = "course_code", length = 30)
    private String courseCode;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal credits;

    @Column(length = 5)
    private String grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_status", nullable = false, length = 20)
    private CourseCompletionStatus completionStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CourseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_unit_id")
    private AcademicUnit academicUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_status", nullable = false, length = 30)
    private CourseMappingStatus mappingStatus;

    @Column(name = "retake_group_key", length = 100)
    private String retakeGroupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private RecordSourceType sourceType;
}
