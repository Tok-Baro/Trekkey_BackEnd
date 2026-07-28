package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.service.dto.BlockchainApprovalView;
import com.api.trekkey.domain.credential.service.dto.IssuerKeyView;
import com.api.trekkey.domain.credential.service.dto.SealedBatchView;
import com.api.trekkey.domain.credential.service.dto.StatusChangeCommand;

public interface CredentialBlockchainService {

    IssuerKeyView syncIssuerKey(Long organizationId, int keyVersion, String signerRef);

    SealedBatchView sealBatch(Long organizationId, String schemaProfileId, int keyVersion);

    BlockchainApprovalView getBatchApproval(Long organizationId, String batchPublicId);

    BlockchainApprovalView renewBatchApproval(Long organizationId, String batchPublicId);

    SealedBatchView reconcileBatch(Long organizationId, String batchPublicId);

    SealedBatchView approveBatch(Long organizationId, String batchPublicId, String signatureHex);

    BlockchainApprovalView requestStatusChange(
            Long organizationId,
            Long actorUserId,
            String credentialPublicId,
            StatusChangeCommand command);

    BlockchainApprovalView getStatusApproval(Long organizationId, Long statusEventId);

    BlockchainApprovalView renewStatusApproval(Long organizationId, Long statusEventId);

    void reconcileStatusChange(Long organizationId, Long statusEventId);

    void approveStatusChange(Long organizationId, Long statusEventId, String signatureHex);
}
