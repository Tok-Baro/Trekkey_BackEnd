package com.api.trekkey.domain.graduation.service;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.RecordCompleteness;
import com.api.trekkey.domain.graduation.entity.AcademicUnit;
import com.api.trekkey.domain.graduation.entity.StudentAcademicUnit;
import com.api.trekkey.domain.graduation.entity.StudentAcademicProfile;
import com.api.trekkey.domain.graduation.repository.AcademicUnitRepository;
import com.api.trekkey.domain.graduation.exception.GraduationErrorResponseCode;
import com.api.trekkey.domain.graduation.repository.StudentAcademicProfileRepository;
import com.api.trekkey.domain.graduation.repository.StudentAcademicUnitRepository;
import com.api.trekkey.domain.graduation.web.dto.GraduationProfileReq;
import com.api.trekkey.domain.graduation.web.dto.GraduationProfileRes;
import com.api.trekkey.domain.graduation.web.dto.GraduationProfileRes.OrganizationSummary;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GraduationProfileServiceImpl implements GraduationProfileService {
    private static final String HANSUNG_CODE = "HANSUNG_UNIVERSITY";

    private final UserRepository userRepository;
    private final StudentAcademicProfileRepository profileRepository;
    private final AcademicUnitRepository academicUnitRepository;
    private final StudentAcademicUnitRepository academicUnitSelectionRepository;

    @Override
    @Transactional(readOnly = true)
    public GraduationProfileRes get(Long userId) {
        User user = getSupportedUser(userId);
        return profileRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> emptyResponse(user));
    }

    @Override
    public GraduationProfileRes save(Long userId, GraduationProfileReq request) {
        User user = getSupportedUser(userId);
        validate(request);

        StudentAcademicProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile != null && !Objects.equals(profile.getVersion(), request.version())) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_PROFILE_VERSION_CONFLICT);
        }
        if ((profile == null || profile.getRecordCompleteness() != RecordCompleteness.COMPLETE)
                && request.recordCompleteness() == RecordCompleteness.COMPLETE
                && !request.recordCompletenessConfirmed()) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_PROFILE_INVALID);
        }

        if (profile == null) {
            profile = StudentAcademicProfile.builder()
                    .user(user)
                    .admissionYear(request.admissionYear().shortValue())
                    .curriculumYear(request.curriculumYear().shortValue())
                    .admissionType(request.admissionType())
                    .graduationPath(request.graduationPath())
                    .majorPlanType(request.majorPlanType())
                    .registeredSemesters(request.registeredSemesters().shortValue())
                    .totalCredits(request.totalCredits())
                    .hansungCredits(request.hansungCredits())
                    .transferRecognizedCredits(request.transferRecognizedCredits())
                    .cumulativeGpa(request.cumulativeGpa())
                    .gpaScale(request.gpaScale())
                    .activityPoints(request.activityPoints())
                    .internationalStudent(request.internationalStudent())
                    .teachingProgram(request.teachingProgram())
                    .inputMode(request.inputMode())
                    .recordCompleteness(request.recordCompleteness())
                    .summaryAsOfTerm(blankToNull(request.summaryAsOfTerm()))
                    .expectedGraduationYear(toShort(request.expectedGraduationYear()))
                    .expectedGraduationMonth(toShort(request.expectedGraduationMonth()))
                    .failHistoryStatus(request.failHistoryStatus())
                    .build();
        } else {
            profile.update(
                    request.admissionYear().shortValue(),
                    request.curriculumYear().shortValue(),
                    request.admissionType(),
                    request.graduationPath(),
                    request.majorPlanType(),
                    request.registeredSemesters().shortValue(),
                    request.totalCredits(),
                    request.hansungCredits(),
                    request.transferRecognizedCredits(),
                    request.cumulativeGpa(),
                    request.gpaScale(),
                    request.activityPoints(),
                    request.internationalStudent(),
                    request.teachingProgram(),
                    request.inputMode(),
                    request.recordCompleteness(),
                    blankToNull(request.summaryAsOfTerm()),
                    toShort(request.expectedGraduationYear()),
                    toShort(request.expectedGraduationMonth()),
                    request.failHistoryStatus());
        }

        profile = profileRepository.saveAndFlush(profile);
        replaceAcademicUnits(profile, request.academicUnits());
        return toResponse(profile);
    }

    private User getSupportedUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        if (!HANSUNG_CODE.equals(user.getOrganization().getCode())) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_UNSUPPORTED_ORGANIZATION);
        }
        return user;
    }

    private void validate(GraduationProfileReq request) {
        int currentYear = Year.now().getValue();
        boolean invalid = request.admissionYear() > currentYear
                || request.curriculumYear() > currentYear
                || request.curriculumYear() < request.admissionYear()
                || request.cumulativeGpa().compareTo(request.gpaScale()) > 0
                || request.hansungCredits().compareTo(request.totalCredits()) > 0
                || request.transferRecognizedCredits().compareTo(request.totalCredits()) > 0
                || (request.expectedGraduationYear() == null) != (request.expectedGraduationMonth() == null)
                || (request.expectedGraduationYear() != null
                    && request.expectedGraduationYear() < request.admissionYear());
        if (invalid) {
            throw new CustomException(GraduationErrorResponseCode.GRADUATION_PROFILE_INVALID);
        }
    }

    private GraduationProfileRes emptyResponse(User user) {
        return new GraduationProfileRes(
                false, null, organization(user), user.getStudentId(),
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, List.of(), null, null);
    }

    private GraduationProfileRes toResponse(StudentAcademicProfile profile) {
        return new GraduationProfileRes(
                true,
                profile.getPublicId(),
                organization(profile.getUser()),
                profile.getUser().getStudentId(),
                (int) profile.getAdmissionYear(),
                (int) profile.getCurriculumYear(),
                profile.getAdmissionType(),
                profile.getGraduationPath(),
                profile.getMajorPlanType(),
                (int) profile.getRegisteredSemesters(),
                profile.getTotalCredits(),
                profile.getHansungCredits(),
                profile.getTransferRecognizedCredits(),
                profile.getCumulativeGpa(),
                profile.getGpaScale(),
                profile.getActivityPoints(),
                profile.isInternationalStudent(),
                profile.isTeachingProgram(),
                profile.getInputMode(),
                profile.getRecordCompleteness(),
                profile.getSummaryAsOfTerm(),
                nullableInt(profile.getExpectedGraduationYear()),
                nullableInt(profile.getExpectedGraduationMonth()),
                academicUnitSelectionRepository.findAllByProfileIdOrderBySequenceNo(profile.getId()).stream()
                        .map(selection -> new GraduationProfileRes.AcademicUnitSelection(
                                selection.getAcademicUnit().getPublicId(),
                                selection.getAcademicUnit().getName(),
                                selection.getAcademicUnit().getUnitType().name(),
                                selection.getRoleType().name(),
                                selection.getSequenceNo()))
                        .toList(),
                profile.getFailHistoryStatus(),
                profile.getVersion());
    }

    private void replaceAcademicUnits(
            StudentAcademicProfile profile,
            List<GraduationProfileReq.AcademicUnitSelection> requested) {
        List<GraduationProfileReq.AcademicUnitSelection> selections = requested == null ? List.of() : requested;
        Set<String> uniqueRoles = new HashSet<>();
        for (GraduationProfileReq.AcademicUnitSelection selection : selections) {
            String key = selection.roleType().name() + ":" + selection.academicUnitPublicId();
            if (!uniqueRoles.add(key)) {
                throw new CustomException(GraduationErrorResponseCode.GRADUATION_PROFILE_INVALID);
            }
        }
        academicUnitSelectionRepository.deleteAllByProfileId(profile.getId());
        academicUnitSelectionRepository.flush();
        for (GraduationProfileReq.AcademicUnitSelection selection : selections) {
            AcademicUnit unit = academicUnitRepository
                    .findByPublicIdAndOrganizationId(selection.academicUnitPublicId(), profile.getUser().getOrganization().getId())
                    .orElseThrow(() -> new CustomException(GraduationErrorResponseCode.GRADUATION_ACADEMIC_UNIT_NOT_FOUND));
            academicUnitSelectionRepository.save(StudentAcademicUnit.builder()
                    .profile(profile)
                    .academicUnit(unit)
                    .roleType(selection.roleType())
                    .sequenceNo(selection.sequenceNo())
                    .graduationEvidenceStatus(com.api.trekkey.domain.graduation.entity.GraduationTypes.EvidenceStatus.UNKNOWN)
                    .build());
        }
    }

    private Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    private Integer nullableInt(Short value) {
        return value == null ? null : value.intValue();
    }

    private OrganizationSummary organization(User user) {
        return new OrganizationSummary(user.getOrganization().getPublicId(), user.getOrganization().getName());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
