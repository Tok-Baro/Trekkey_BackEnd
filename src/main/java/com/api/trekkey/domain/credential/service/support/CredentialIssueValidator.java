package com.api.trekkey.domain.credential.service.support;

import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.service.CredentialSchemaProfiles;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.global.exception.CustomException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CredentialIssueValidator {

    private CredentialIssueValidator() {
    }

    public static void validate(CredentialIssueCommand command) {
        if (command == null
                || command.issuerOrganizationId() == null
                || isBlank(command.credentialNo())
                || command.credentialType() == null
                || !CredentialSchemaProfiles.supports(command.credentialType(), command.schemaProfileId())
                || command.source() == null
                || command.issuedAt() == null) {
            invalid();
        }

        validateSource(command);
        validateSubjects(command.subjects());
        validateFiles(command);

        if (command.source().finalizedAt().isAfter(command.issuedAt())
                || (command.expiresAt() != null && !command.expiresAt().isAfter(command.issuedAt()))) {
            invalid();
        }
    }

    private static void validateSource(CredentialIssueCommand command) {
        CredentialIssueCommand.Source source = command.source();
        if (source.sourceType() == null
                || isBlank(source.publicId())
                || source.finalizedAt() == null
                || source.snapshot() == null
                || !source.snapshot().isObject()
                || !sourceTypeMatches(command.credentialType(), source.sourceType())) {
            invalid();
        }

        int referenceCount = count(source.teamId()) + count(source.submissionId()) + count(source.awardId());
        boolean matchingReference = switch (source.sourceType()) {
            case TEAM -> source.teamId() != null;
            case SUBMISSION -> source.submissionId() != null;
            case AWARD -> source.awardId() != null;
        };
        if (referenceCount != 1 || !matchingReference) {
            invalid();
        }
    }

    private static void validateSubjects(List<CredentialIssueCommand.Subject> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            invalid();
        }

        Set<Integer> orders = new HashSet<>();
        Set<String> subjectRoles = new HashSet<>();
        int teamCount = 0;
        int userCount = 0;
        for (CredentialIssueCommand.Subject subject : subjects) {
            if (subject == null
                    || subject.subjectType() == null
                    || isBlank(subject.subjectRef())
                    || isBlank(subject.displayName())
                    || isBlank(subject.roleCode())
                    || subject.disclosureClass() == null
                    || subject.order() < 0
                    || !orders.add(subject.order())
                    || !subjectRoles.add(subject.subjectRef() + "\u0000" + subject.roleCode())) {
                invalid();
            }

            boolean user = subject.subjectType() == CredentialSubjectType.USER
                    && subject.userId() != null
                    && subject.teamId() == null;
            boolean team = subject.subjectType() == CredentialSubjectType.TEAM
                    && subject.teamId() != null
                    && subject.userId() == null;
            if (!user && !team) {
                invalid();
            }
            userCount += user ? 1 : 0;
            teamCount += team ? 1 : 0;
        }
        if (teamCount != 1 || userCount == 0) {
            invalid();
        }
    }

    private static void validateFiles(CredentialIssueCommand command) {
        if (command.files() == null || (command.credentialType() == CredentialType.WORK && command.files().isEmpty())) {
            invalid();
        }
        for (CredentialIssueCommand.FileEvidence file : command.files()) {
            if (file == null
                    || isBlank(file.originalName())
                    || isBlank(file.contentType())
                    || file.sizeBytes() < 0
                    || isBlank(file.sha256Hex())) {
                invalid();
            }
        }
    }

    private static boolean sourceTypeMatches(CredentialType credentialType, CredentialSourceType sourceType) {
        return switch (credentialType) {
            case PARTICIPATION -> sourceType == CredentialSourceType.TEAM;
            case WORK -> sourceType == CredentialSourceType.SUBMISSION;
            case AWARD -> sourceType == CredentialSourceType.AWARD;
        };
    }

    private static int count(Long value) {
        return value == null ? 0 : 1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void invalid() {
        throw new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT);
    }
}
