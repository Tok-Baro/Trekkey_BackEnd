package com.api.trekkey.domain.credential.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Documents the immutable source facts used to issue a credential without retaining mutable
 * business tables or numeric source versions in the fingerprint.
 */
public final class SourceFingerprint {

    public static final String PROFILE_ID = "trekkey:source-fingerprint:v1";

    private SourceFingerprint() {
    }

    public static Result build(Input input) {
        if (input == null) {
            throw new CryptoValidationException("source fingerprint input must not be null");
        }

        byte[] sourceSnapshotCanonicalBytes = CanonicalJson.canonicalize(input.sourceSnapshot());
        Hash32 sourceSnapshotHash = Hashing.sha256(sourceSnapshotCanonicalBytes);
        List<SubjectSnapshot> sortedSubjects = sortedSubjects(input.subjects());
        ArrayNode subjectsNode = JsonNodeFactory.instance.arrayNode();
        for (SubjectSnapshot subject : sortedSubjects) {
            ObjectNode subjectNode = subjectsNode.addObject();
            subjectNode.put("roleCode", CanonicalJson.normalizeNfc(subject.roleCode(), "roleCode"));
            subjectNode.put("subjectPublicId", CanonicalJson.normalizeNfc(subject.subjectPublicId(), "subjectPublicId"));
            subjectNode.set("snapshot", subject.immutableSnapshot());
        }
        byte[] subjectSetCanonicalBytes = CanonicalJson.canonicalize(subjectsNode);
        Hash32 subjectSetHash = Hashing.sha256(subjectSetCanonicalBytes);

        ObjectNode fingerprintNode = JsonNodeFactory.instance.objectNode();
        fingerprintNode.put("credentialType", CanonicalJson.normalizeNfc(input.credentialType(), "credentialType"));
        fingerprintNode.put("fingerprintProfileId", PROFILE_ID);
        fingerprintNode.put("issuerPublicId", CanonicalJson.normalizeNfc(input.issuerPublicId(), "issuerPublicId"));
        fingerprintNode.put("schemaProfileId", CanonicalJson.normalizeNfc(input.schemaProfileId(), "schemaProfileId"));
        fingerprintNode.put("sourcePublicId", CanonicalJson.normalizeNfc(input.sourcePublicId(), "sourcePublicId"));
        fingerprintNode.put("sourceSnapshotHash", sourceSnapshotHash.hex());
        fingerprintNode.put("sourceType", CanonicalJson.normalizeNfc(input.sourceType(), "sourceType"));
        fingerprintNode.put("subjectSetHash", subjectSetHash.hex());
        byte[] fingerprintCanonicalBytes = CanonicalJson.canonicalize(fingerprintNode);

        return new Result(
            sourceSnapshotCanonicalBytes,
            sourceSnapshotHash,
            subjectSetCanonicalBytes,
            subjectSetHash,
            fingerprintCanonicalBytes,
            Hashing.sha256(fingerprintCanonicalBytes)
        );
    }

    public record Input(
        String issuerPublicId,
        String credentialType,
        String sourceType,
        String sourcePublicId,
        JsonNode sourceSnapshot,
        List<SubjectSnapshot> subjects,
        String schemaProfileId
    ) {
        public Input {
            requireNonBlank(issuerPublicId, "issuerPublicId");
            requireNonBlank(credentialType, "credentialType");
            requireNonBlank(sourceType, "sourceType");
            requireNonBlank(sourcePublicId, "sourcePublicId");
            requireNonBlank(schemaProfileId, "schemaProfileId");
            if (sourceSnapshot == null || sourceSnapshot.isNull()) {
                throw new CryptoValidationException("sourceSnapshot must not be null");
            }
            if (subjects == null || subjects.isEmpty()) {
                throw new CryptoValidationException("source subjects must not be empty");
            }
            sourceSnapshot = sourceSnapshot.deepCopy();
            subjects = List.copyOf(subjects);
        }

        @Override
        public JsonNode sourceSnapshot() {
            return sourceSnapshot.deepCopy();
        }
    }

    public record SubjectSnapshot(String subjectPublicId, String roleCode, JsonNode immutableSnapshot) {
        public SubjectSnapshot {
            requireNonBlank(subjectPublicId, "subjectPublicId");
            requireNonBlank(roleCode, "roleCode");
            if (immutableSnapshot == null || immutableSnapshot.isNull()) {
                throw new CryptoValidationException("subject immutableSnapshot must not be null");
            }
            immutableSnapshot = immutableSnapshot.deepCopy();
        }

        @Override
        public JsonNode immutableSnapshot() {
            return immutableSnapshot.deepCopy();
        }
    }

    public record Result(
        byte[] sourceSnapshotCanonicalBytes,
        Hash32 sourceSnapshotHash,
        byte[] subjectSetCanonicalBytes,
        Hash32 subjectSetHash,
        byte[] fingerprintCanonicalBytes,
        Hash32 fingerprintHash
    ) {
        public Result {
            sourceSnapshotCanonicalBytes = sourceSnapshotCanonicalBytes.clone();
            subjectSetCanonicalBytes = subjectSetCanonicalBytes.clone();
            fingerprintCanonicalBytes = fingerprintCanonicalBytes.clone();
        }

        @Override
        public byte[] sourceSnapshotCanonicalBytes() {
            return sourceSnapshotCanonicalBytes.clone();
        }

        @Override
        public byte[] subjectSetCanonicalBytes() {
            return subjectSetCanonicalBytes.clone();
        }

        @Override
        public byte[] fingerprintCanonicalBytes() {
            return fingerprintCanonicalBytes.clone();
        }
    }

    private static List<SubjectSnapshot> sortedSubjects(List<SubjectSnapshot> subjects) {
        List<SubjectSnapshot> sorted = subjects.stream()
            .sorted(Comparator.comparing((SubjectSnapshot subject) -> CanonicalJson.normalizeNfc(subject.subjectPublicId(), "subjectPublicId"))
                .thenComparing(subject -> CanonicalJson.normalizeNfc(subject.roleCode(), "roleCode")))
            .toList();
        Set<String> keys = new HashSet<>();
        for (SubjectSnapshot subject : sorted) {
            String key = CanonicalJson.normalizeNfc(subject.subjectPublicId(), "subjectPublicId")
                + "\u0000" + CanonicalJson.normalizeNfc(subject.roleCode(), "roleCode");
            if (!keys.add(key)) {
                throw new CryptoValidationException("subjectPublicId and roleCode must be unique in a source fingerprint");
            }
        }
        return sorted;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CryptoValidationException(fieldName + " must not be blank");
        }
    }
}
