package com.api.trekkey.domain.evidence.entity;

import com.api.trekkey.domain.graduation.entity.StudentNonCourseRecord;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "evidence_binding")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EvidenceBinding extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false, unique = true, updatable = false)
    private VerificationDecision decision;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_non_course_record_id", nullable = false, unique = true, updatable = false)
    private StudentNonCourseRecord studentNonCourseRecord;
    @Column(name = "bound_at", nullable = false, updatable = false)
    private LocalDateTime boundAt;
}
