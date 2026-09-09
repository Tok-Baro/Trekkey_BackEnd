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
        PublicDetails publicDetails,
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

    public record PublicDetails(
            String sourceType,
            String sourcePublicId,
            Instant finalizedAt,
            String contestTitle,
            String teamName,
            String submissionTitle,
            String prize,
            Integer awardRankNo) {
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
            long chainId,
            String contractAddress,
            String transactionHash,
            Long blockNumber,
            ChainMetadata blockchain) {

        /** Keep the existing JSON fields and Java construction contract for legacy consumers. */
        public Evidence(boolean canonicalPayloadMatches, boolean contentHashMatches, boolean fileManifestHashMatches,
                boolean credentialClaimsMatch, boolean credentialIdMatches, boolean merkleProofMatches,
                String issuerId, String credentialIdHash, String schemaVersionHash, String contentHash,
                String fileManifestHash, String leafHash, String batchPublicId, String batchIdHash, String merkleRoot,
                Integer treeVersion, List<String> merkleProof, long chainId, String contractAddress,
                String transactionHash, Long blockNumber) {
            this(canonicalPayloadMatches, contentHashMatches, fileManifestHashMatches, credentialClaimsMatch,
                    credentialIdMatches, merkleProofMatches, issuerId, credentialIdHash, schemaVersionHash,
                    contentHash, fileManifestHash, leafHash, batchPublicId, batchIdHash, merkleRoot, treeVersion,
                    merkleProof, chainId, contractAddress, transactionHash, blockNumber, null);
        }

        public Evidence {
            merkleProof = merkleProof == null ? List.of() : List.copyOf(merkleProof);
        }
    }

    public record ChainMetadata(String provider, String network, String chainIdentifier, String packageId,
            String registryObjectId, String transactionDigest, Long checkpointSequenceNumber,
            String checkpointDigest, String explorerUrl, String approvalScheme) {}
}
