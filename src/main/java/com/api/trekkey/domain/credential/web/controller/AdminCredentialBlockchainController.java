package com.api.trekkey.domain.credential.web.controller;

import com.api.trekkey.domain.credential.service.AuthenticatedOrganizationResolver;
import com.api.trekkey.domain.credential.service.CredentialBlockchainService;
import com.api.trekkey.domain.credential.service.dto.BlockchainApprovalView;
import com.api.trekkey.domain.credential.service.dto.CredentialStatusEventView;
import com.api.trekkey.domain.credential.service.dto.IssuerKeyView;
import com.api.trekkey.domain.credential.service.dto.SealedBatchView;
import com.api.trekkey.domain.credential.web.dto.BatchSealRequest;
import com.api.trekkey.domain.credential.web.dto.IssuerKeySyncRequest;
import com.api.trekkey.domain.credential.web.dto.SignatureApprovalRequest;
import com.api.trekkey.domain.credential.web.dto.StatusChangeRequest;
import com.api.trekkey.global.response.SuccessResponse;
import com.api.trekkey.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/admin/blockchain")
public class AdminCredentialBlockchainController {

    private final CredentialBlockchainService credentialBlockchainService;
    private final AuthenticatedOrganizationResolver authenticatedOrganizationResolver;

    @PostMapping("/issuer-keys/{keyVersion}/sync")
    public ResponseEntity<SuccessResponse<IssuerKeyView>> syncIssuerKey(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Min(value = 1, message = "keyVersion은 1 이상이어야 합니다.") int keyVersion,
            @Valid @RequestBody IssuerKeySyncRequest request) {
        Long organizationId = organizationId(principal);
        IssuerKeyView response = credentialBlockchainService.syncIssuerKey(
                organizationId,
                keyVersion,
                request.signerRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.create(response));
    }

    @PostMapping("/batches")
    public ResponseEntity<SuccessResponse<SealedBatchView>> sealBatch(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BatchSealRequest request) {
        Long organizationId = organizationId(principal);
        SealedBatchView response = credentialBlockchainService.sealBatch(
                organizationId,
                request.schemaProfileId(),
                request.keyVersion());
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.create(response));
    }

    @GetMapping("/batches")
    public ResponseEntity<SuccessResponse<List<SealedBatchView>>> getBatches(
            @AuthenticationPrincipal AuthPrincipal principal) {
        Long organizationId = organizationId(principal);
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialBlockchainService.getBatches(organizationId)));
    }

    @GetMapping("/batches/{batchPublicId}/approval")
    public ResponseEntity<SuccessResponse<BlockchainApprovalView>> getBatchApproval(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String batchPublicId) {
        Long organizationId = organizationId(principal);
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialBlockchainService.getBatchApproval(organizationId, batchPublicId)));
    }

    @PostMapping("/batches/{batchPublicId}/approval/renew")
    public ResponseEntity<SuccessResponse<BlockchainApprovalView>> renewBatchApproval(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String batchPublicId) {
        Long organizationId = organizationId(principal);
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialBlockchainService.renewBatchApproval(organizationId, batchPublicId)));
    }

    @PostMapping("/batches/{batchPublicId}/reconcile")
    public ResponseEntity<SuccessResponse<SealedBatchView>> reconcileBatch(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String batchPublicId) {
        Long organizationId = organizationId(principal);
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialBlockchainService.reconcileBatch(organizationId, batchPublicId)));
    }

    @PostMapping("/batches/{batchPublicId}/approval")
    public ResponseEntity<SuccessResponse<SealedBatchView>> approveBatch(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String batchPublicId,
            @Valid @RequestBody SignatureApprovalRequest request) {
        Long organizationId = organizationId(principal);
        SealedBatchView response = credentialBlockchainService.approveBatch(
                organizationId,
                batchPublicId,
                request.signatureHex());
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    @PostMapping("/credentials/{credentialPublicId}/status-events")
    public ResponseEntity<SuccessResponse<BlockchainApprovalView>> requestStatusChange(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String credentialPublicId,
            @Valid @RequestBody StatusChangeRequest request) {
        Long actorUserId = principalId(principal);
        Long organizationId = authenticatedOrganizationResolver.resolve(actorUserId);
        BlockchainApprovalView response = credentialBlockchainService.requestStatusChange(
                organizationId,
                actorUserId,
                credentialPublicId,
                request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.create(response));
    }

    @GetMapping("/status-events")
    public ResponseEntity<SuccessResponse<List<CredentialStatusEventView>>>
            getStatusEvents(
                    @AuthenticationPrincipal AuthPrincipal principal) {
        Long organizationId = organizationId(principal);
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialBlockchainService.getStatusEvents(
                        organizationId)));
    }

    @GetMapping("/status-events/{statusEventId}/approval")
    public ResponseEntity<SuccessResponse<BlockchainApprovalView>> getStatusApproval(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Min(value = 1, message = "statusEventId는 1 이상이어야 합니다.") Long statusEventId) {
        Long organizationId = organizationId(principal);
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialBlockchainService.getStatusApproval(organizationId, statusEventId)));
    }

    @PostMapping("/status-events/{statusEventId}/approval/renew")
    public ResponseEntity<SuccessResponse<BlockchainApprovalView>> renewStatusApproval(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Min(value = 1, message = "statusEventId는 1 이상이어야 합니다.") Long statusEventId) {
        Long organizationId = organizationId(principal);
        return ResponseEntity.ok(SuccessResponse.ok(
                credentialBlockchainService.renewStatusApproval(organizationId, statusEventId)));
    }

    @PostMapping("/status-events/{statusEventId}/reconcile")
    public ResponseEntity<SuccessResponse<?>> reconcileStatusChange(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Min(value = 1, message = "statusEventId는 1 이상이어야 합니다.") Long statusEventId) {
        Long organizationId = organizationId(principal);
        credentialBlockchainService.reconcileStatusChange(organizationId, statusEventId);
        return ResponseEntity.ok(SuccessResponse.empty());
    }

    @PostMapping("/status-events/{statusEventId}/approval")
    public ResponseEntity<SuccessResponse<?>> approveStatusChange(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Min(value = 1, message = "statusEventId는 1 이상이어야 합니다.") Long statusEventId,
            @Valid @RequestBody SignatureApprovalRequest request) {
        Long organizationId = organizationId(principal);
        credentialBlockchainService.approveStatusChange(organizationId, statusEventId, request.signatureHex());
        return ResponseEntity.ok(SuccessResponse.empty());
    }

    private Long organizationId(AuthPrincipal principal) {
        return authenticatedOrganizationResolver.resolve(principalId(principal));
    }

    private Long principalId(AuthPrincipal principal) {
        return principal.getId();
    }
}
