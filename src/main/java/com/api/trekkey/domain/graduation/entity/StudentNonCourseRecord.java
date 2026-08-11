package com.api.trekkey.domain.graduation.entity;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.NonCourseRecordType;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.VerificationStatus;
import com.api.trekkey.domain.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "student_non_course_record", indexes = @Index(
        name = "idx_student_non_course_evaluation", columnList = "profile_id,record_type,verification_status"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentNonCourseRecord extends GraduationPublicEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private StudentAcademicProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 40)
    private NonCourseRecordType recordType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "numeric_value", precision = 10, scale = 2)
    private BigDecimal numericValue;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(length = 1000)
    private String note;
}
