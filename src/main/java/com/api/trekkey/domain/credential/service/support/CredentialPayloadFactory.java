package com.api.trekkey.domain.credential.service.support;

import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class CredentialPayloadFactory {

    private CredentialPayloadFactory() {
    }

    public static JsonNode create(
            String credentialPublicId,
            CredentialIssueCommand command,
            String issuerPublicId,
            String issuerName,
            String fileManifestHashHex) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("credentialId", credentialPublicId);
        root.put("credentialNo", command.credentialNo());
        root.put("credentialType", command.credentialType().name());
        root.put("schemaProfileId", command.schemaProfileId());
        root.put("issuedAt", command.issuedAt().toString());
        putNullableInstant(root, "expiresAt", command.expiresAt());
        root.put("fileManifestHash", fileManifestHashHex);

        ObjectNode issuer = root.putObject("issuer");
        issuer.put("publicId", issuerPublicId);
        issuer.put("name", issuerName);

        CredentialIssueCommand.Source source = command.source();
        ObjectNode sourceNode = root.putObject("source");
        sourceNode.put("type", source.sourceType().name());
        sourceNode.put("publicId", source.publicId());
        sourceNode.put("finalizedAt", source.finalizedAt().toString());
        sourceNode.set("snapshot", source.snapshot());

        ArrayNode subjects = root.putArray("subjects");
        sortedSubjects(command.subjects()).forEach(subject -> {
            ObjectNode subjectNode = subjects.addObject();
            subjectNode.put("ref", subject.subjectRef());
            subjectNode.put("type", subject.subjectType().name());
            subjectNode.put("displayName", subject.displayName());
            if (subject.major() == null) {
                subjectNode.putNull("major");
            } else {
                subjectNode.put("major", subject.major());
            }
            subjectNode.put("roleCode", subject.roleCode());
            subjectNode.put("disclosureClass", subject.disclosureClass().name());
            subjectNode.put("order", subject.order());
        });

        return root;
    }

    public static List<CredentialIssueCommand.Subject> sortedSubjects(
            List<CredentialIssueCommand.Subject> subjects) {
        return subjects.stream()
                .sorted(Comparator.comparingInt(CredentialIssueCommand.Subject::order)
                        .thenComparing(CredentialIssueCommand.Subject::subjectRef))
                .toList();
    }

    private static void putNullableInstant(ObjectNode node, String fieldName, Instant value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value.toString());
        }
    }
}
