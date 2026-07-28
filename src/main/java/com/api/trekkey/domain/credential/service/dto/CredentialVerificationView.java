package com.api.trekkey.domain.credential.service.dto;

import com.api.trekkey.domain.credential.entity.CredentialType;
import java.time.Instant;
import java.util.List;

public record CredentialVerificationView(
        CredentialVerificationStatus verificationStatus,
        String credentialPublicId,
        String credentialNo,
        CredentialType credentialType,
        String schemaProfileId,
        String issuerPublicId,
        String issuerName,
        Instant issuedAt,
        Instant expiresAt,
        List<PublicSubject> publicSubjects,
        Evidence evidence,
        String replacementCredentialPublicId,
        String replacementCredentialIdHash) {

    public CredentialVerificationView {
        publicSubjects = List.copyOf(publicSubjects);
    }

    public record PublicSubject(
            String subjectRef,
            String subjectType,
            String displayName,
            String major,
            String roleCode) {
    }

    public record Evidence(
            boolean canonicalPayloadMatches,
            boolean contentHashMatches,
            boolean fileManifestHashMatches,
            boolean credentialClaimsMatch,
            boolean credentialIdMatches,
            boolean merkleProofMatches,
            String issuerId,
            String credentialIdHash,
            String schemaVersionHash,
            String contentHash,
            String fileManifestHash,
            String leafHash,
            String batchPublicId,
            String batchIdHash,
            String merkleRoot,
            Integer treeVersion,
            List<String> merkleProof,
            Long chainId,
            String contractAddress,
            String contractVersion,
            String transactionHash,
            Long blockNumber) {

        public Evidence {
            merkleProof = merkleProof == null ? List.of() : List.copyOf(merkleProof);
        }
    }
}
