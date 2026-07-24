// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {MerkleProof} from "@openzeppelin/contracts/utils/cryptography/MerkleProof.sol";

/**
 * @title TrekkeyCredentialRegistryV1
 * @notice Stores only credential roots and correction status. Credential payloads remain off-chain.
 */
contract TrekkeyCredentialRegistryV1 is AccessControl, Pausable, EIP712 {
    bytes32 public constant ISSUER_KEY_ADMIN_ROLE = keccak256("ISSUER_KEY_ADMIN_ROLE");
    bytes32 public constant RELAYER_ROLE = keccak256("RELAYER_ROLE");
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");

    bytes32 public constant LEAF_DOMAIN = keccak256("TREKKEY_CREDENTIAL_LEAF_V1");

    bytes32 public constant BATCH_APPROVAL_TYPEHASH = keccak256(
        "BatchApproval(bytes32 issuerId,bytes32 batchIdHash,bytes32 merkleRoot,bytes32 schemaVersionHash,uint32 leafCount,uint16 treeVersion,uint64 issuerKeyVersion,uint64 approvalNonce,uint64 deadline)"
    );
    bytes32 public constant STATUS_APPROVAL_TYPEHASH = keccak256(
        "StatusApproval(bytes32 issuerId,bytes32 credentialIdHash,uint8 action,bytes32 replacementCredentialIdHash,uint64 effectiveAt,uint64 issuerKeyVersion,uint64 approvalNonce,uint64 deadline)"
    );

    enum CredentialState {
        NONE,
        REVOKED,
        SUPERSEDED
    }

    enum StatusAction {
        REVOKE,
        SUPERSEDE
    }

    struct BatchApproval {
        bytes32 issuerId;
        bytes32 batchIdHash;
        bytes32 merkleRoot;
        bytes32 schemaVersionHash;
        uint32 leafCount;
        uint16 treeVersion;
        uint64 issuerKeyVersion;
        uint64 approvalNonce;
        uint64 deadline;
    }

    struct StatusApproval {
        bytes32 issuerId;
        bytes32 credentialIdHash;
        StatusAction action;
        bytes32 replacementCredentialIdHash;
        uint64 effectiveAt;
        uint64 issuerKeyVersion;
        uint64 approvalNonce;
        uint64 deadline;
    }

    struct BatchAnchor {
        bytes32 issuerId;
        bytes32 merkleRoot;
        bytes32 schemaVersionHash;
        uint32 leafCount;
        uint16 treeVersion;
        uint64 issuerKeyVersion;
        uint64 anchoredAt;
    }

    struct IssuerKey {
        address signer;
        uint64 validFrom;
        uint64 validUntil;
        uint64 compromisedAt;
    }

    struct StatusRecord {
        CredentialState state;
        uint64 effectiveAt;
        uint64 recordedAt;
        uint64 issuerKeyVersion;
        bytes32 replacementCredentialIdHash;
    }

    error ZeroAddress();
    error ZeroValue();
    error IssuerKeyAlreadyExists(bytes32 issuerId, uint64 keyVersion);
    error IssuerKeyNotFound(bytes32 issuerId, uint64 keyVersion);
    error InvalidIssuerKeyTimestamp(uint64 timestamp);
    error IssuerKeyAlreadyRetired(bytes32 issuerId, uint64 keyVersion);
    error IssuerKeyAlreadyCompromised(bytes32 issuerId, uint64 keyVersion);
    error IssuerKeyNotActive(bytes32 issuerId, uint64 keyVersion);
    error IssuerKeyIsCompromised(bytes32 issuerId, uint64 keyVersion);
    error ApprovalExpired(uint64 deadline);
    error ApprovalDigestAlreadyUsed(bytes32 approvalDigest);
    error ApprovalNonceAlreadyUsed(bytes32 issuerId, uint64 approvalNonce);
    error InvalidIssuerSignature(bytes32 issuerId, uint64 keyVersion, address recoveredSigner);
    error BatchAlreadyAnchored(bytes32 batchIdHash);
    error InvalidBatchApproval();
    error InvalidStatusApproval();
    error InvalidStatusEffectiveAt(uint64 effectiveAt);
    error InvalidStatusAction(StatusAction expected, StatusAction actual);
    error CredentialStatusAlreadySet(bytes32 issuerId, bytes32 credentialIdHash);

    event IssuerKeyRegistered(bytes32 indexed issuerId, uint64 indexed keyVersion, address indexed signer, uint64 validFrom);
    event IssuerKeyRetired(bytes32 indexed issuerId, uint64 indexed keyVersion, uint64 validUntil);
    event IssuerKeyCompromised(bytes32 indexed issuerId, uint64 indexed keyVersion, uint64 compromisedAt);
    event BatchAnchored(
        bytes32 indexed issuerId,
        bytes32 indexed batchIdHash,
        bytes32 indexed merkleRoot,
        bytes32 schemaVersionHash,
        uint32 leafCount,
        uint16 treeVersion,
        uint64 issuerKeyVersion,
        uint64 anchoredAt
    );
    event CredentialRevoked(
        bytes32 indexed issuerId,
        bytes32 indexed credentialIdHash,
        uint64 effectiveAt,
        uint64 issuerKeyVersion
    );
    event CredentialSuperseded(
        bytes32 indexed issuerId,
        bytes32 indexed credentialIdHash,
        bytes32 replacementCredentialIdHash,
        uint64 effectiveAt,
        uint64 issuerKeyVersion
    );

    mapping(bytes32 batchIdHash => BatchAnchor batch) private _batches;
    mapping(bytes32 batchIdHash => bool exists) private _batchExists;
    mapping(bytes32 issuerId => mapping(uint64 keyVersion => IssuerKey key)) private _issuerKeys;
    mapping(bytes32 issuerId => mapping(bytes32 credentialIdHash => StatusRecord status)) private _credentialStatuses;
    mapping(bytes32 approvalDigest => bool used) public usedApprovalDigest;
    mapping(bytes32 issuerId => mapping(uint64 approvalNonce => bool used)) private _usedApprovalNonces;

    constructor(address initialAdmin) EIP712("TrekkeyCredentialRegistry", "1") {
        if (initialAdmin == address(0)) revert ZeroAddress();

        _grantRole(DEFAULT_ADMIN_ROLE, initialAdmin);
        _grantRole(ISSUER_KEY_ADMIN_ROLE, initialAdmin);
        _grantRole(RELAYER_ROLE, initialAdmin);
        _grantRole(PAUSER_ROLE, initialAdmin);
    }

    function pause() external onlyRole(PAUSER_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(PAUSER_ROLE) {
        _unpause();
    }

    function registerIssuerKey(bytes32 issuerId, uint64 keyVersion, address signer)
        external
        onlyRole(ISSUER_KEY_ADMIN_ROLE)
    {
        if (issuerId == bytes32(0) || keyVersion == 0) revert ZeroValue();
        if (signer == address(0)) revert ZeroAddress();
        if (_issuerKeys[issuerId][keyVersion].signer != address(0)) {
            revert IssuerKeyAlreadyExists(issuerId, keyVersion);
        }

        uint64 validFrom = _currentTimestamp();
        _issuerKeys[issuerId][keyVersion] = IssuerKey({
            signer: signer,
            validFrom: validFrom,
            validUntil: 0,
            compromisedAt: 0
        });

        emit IssuerKeyRegistered(issuerId, keyVersion, signer, validFrom);
    }

    function retireIssuerKey(bytes32 issuerId, uint64 keyVersion, uint64 validUntil)
        external
        onlyRole(ISSUER_KEY_ADMIN_ROLE)
    {
        IssuerKey storage issuerKey = _getIssuerKeyStorage(issuerId, keyVersion);
        if (issuerKey.validUntil != 0) revert IssuerKeyAlreadyRetired(issuerId, keyVersion);
        if (validUntil < issuerKey.validFrom || validUntil > _currentTimestamp()) {
            revert InvalidIssuerKeyTimestamp(validUntil);
        }

        issuerKey.validUntil = validUntil;
        emit IssuerKeyRetired(issuerId, keyVersion, validUntil);
    }

    function markIssuerKeyCompromised(bytes32 issuerId, uint64 keyVersion, uint64 compromisedAt)
        external
        onlyRole(ISSUER_KEY_ADMIN_ROLE)
    {
        IssuerKey storage issuerKey = _getIssuerKeyStorage(issuerId, keyVersion);
        if (issuerKey.compromisedAt != 0) revert IssuerKeyAlreadyCompromised(issuerId, keyVersion);
        if (compromisedAt < issuerKey.validFrom || compromisedAt > _currentTimestamp()) {
            revert InvalidIssuerKeyTimestamp(compromisedAt);
        }

        issuerKey.compromisedAt = compromisedAt;
        emit IssuerKeyCompromised(issuerId, keyVersion, compromisedAt);
    }

    /// @notice Anchoring is the only operation stopped by pause; correction operations remain available.
    function anchorBatch(BatchApproval calldata approval, bytes calldata issuerSignature)
        external
        onlyRole(RELAYER_ROLE)
        whenNotPaused
    {
        if (
            approval.issuerId == bytes32(0) || approval.batchIdHash == bytes32(0)
                || approval.merkleRoot == bytes32(0) || approval.schemaVersionHash == bytes32(0)
                || approval.leafCount == 0 || approval.treeVersion == 0
        ) {
            revert InvalidBatchApproval();
        }

        _consumeBatchApproval(approval, issuerSignature);
        if (_batchExists[approval.batchIdHash]) revert BatchAlreadyAnchored(approval.batchIdHash);

        uint64 anchoredAt = _currentTimestamp();
        _batches[approval.batchIdHash] = BatchAnchor({
            issuerId: approval.issuerId,
            merkleRoot: approval.merkleRoot,
            schemaVersionHash: approval.schemaVersionHash,
            leafCount: approval.leafCount,
            treeVersion: approval.treeVersion,
            issuerKeyVersion: approval.issuerKeyVersion,
            anchoredAt: anchoredAt
        });
        _batchExists[approval.batchIdHash] = true;

        emit BatchAnchored(
            approval.issuerId,
            approval.batchIdHash,
            approval.merkleRoot,
            approval.schemaVersionHash,
            approval.leafCount,
            approval.treeVersion,
            approval.issuerKeyVersion,
            anchoredAt
        );
    }

    function revokeCredential(StatusApproval calldata approval, bytes calldata issuerSignature)
        external
        onlyRole(RELAYER_ROLE)
    {
        _setCredentialStatus(approval, issuerSignature, StatusAction.REVOKE);
    }

    function supersedeCredential(StatusApproval calldata approval, bytes calldata issuerSignature)
        external
        onlyRole(RELAYER_ROLE)
    {
        _setCredentialStatus(approval, issuerSignature, StatusAction.SUPERSEDE);
    }

    function getBatch(bytes32 batchIdHash) external view returns (BatchAnchor memory batch, bool exists) {
        return (_batches[batchIdHash], _batchExists[batchIdHash]);
    }

    function getIssuerKey(bytes32 issuerId, uint64 keyVersion) external view returns (IssuerKey memory) {
        return _issuerKeys[issuerId][keyVersion];
    }

    function getCredentialStatus(bytes32 issuerId, bytes32 credentialIdHash)
        external
        view
        returns (StatusRecord memory)
    {
        return _credentialStatuses[issuerId][credentialIdHash];
    }

    function isApprovalNonceUsed(bytes32 issuerId, uint64 approvalNonce) external view returns (bool) {
        return _usedApprovalNonces[issuerId][approvalNonce];
    }

    function verifyProof(bytes32 batchIdHash, bytes32 leafHash, bytes32[] calldata proof)
        external
        view
        returns (bool)
    {
        if (!_batchExists[batchIdHash]) return false;
        return MerkleProof.verifyCalldata(proof, _batches[batchIdHash].merkleRoot, leafHash);
    }

    function hashLeaf(
        bytes32 issuerId,
        bytes32 credentialIdHash,
        bytes32 schemaVersionHash,
        bytes32 contentHash,
        bytes32 fileManifestHash
    ) external pure returns (bytes32) {
        bytes memory leafValue = abi.encode(
            LEAF_DOMAIN,
            issuerId,
            credentialIdHash,
            schemaVersionHash,
            contentHash,
            fileManifestHash
        );
        return keccak256(bytes.concat(keccak256(leafValue)));
    }

    function _setCredentialStatus(
        StatusApproval calldata approval,
        bytes calldata issuerSignature,
        StatusAction expectedAction
    ) private {
        if (approval.issuerId == bytes32(0) || approval.credentialIdHash == bytes32(0)) {
            revert InvalidStatusApproval();
        }
        if (approval.effectiveAt == 0 || approval.effectiveAt > _currentTimestamp()) {
            revert InvalidStatusEffectiveAt(approval.effectiveAt);
        }
        if (approval.action != expectedAction) revert InvalidStatusAction(expectedAction, approval.action);
        if (
            expectedAction == StatusAction.REVOKE && approval.replacementCredentialIdHash != bytes32(0)
        ) {
            revert InvalidStatusApproval();
        }
        if (
            expectedAction == StatusAction.SUPERSEDE
                && (approval.replacementCredentialIdHash == bytes32(0)
                    || approval.replacementCredentialIdHash == approval.credentialIdHash)
        ) {
            revert InvalidStatusApproval();
        }

        _consumeStatusApproval(approval, issuerSignature);
        if (_credentialStatuses[approval.issuerId][approval.credentialIdHash].state != CredentialState.NONE) {
            revert CredentialStatusAlreadySet(approval.issuerId, approval.credentialIdHash);
        }

        CredentialState state = expectedAction == StatusAction.REVOKE
            ? CredentialState.REVOKED
            : CredentialState.SUPERSEDED;
        uint64 recordedAt = _currentTimestamp();
        _credentialStatuses[approval.issuerId][approval.credentialIdHash] = StatusRecord({
            state: state,
            effectiveAt: approval.effectiveAt,
            recordedAt: recordedAt,
            issuerKeyVersion: approval.issuerKeyVersion,
            replacementCredentialIdHash: approval.replacementCredentialIdHash
        });

        if (state == CredentialState.REVOKED) {
            emit CredentialRevoked(
                approval.issuerId,
                approval.credentialIdHash,
                approval.effectiveAt,
                approval.issuerKeyVersion
            );
        } else {
            emit CredentialSuperseded(
                approval.issuerId,
                approval.credentialIdHash,
                approval.replacementCredentialIdHash,
                approval.effectiveAt,
                approval.issuerKeyVersion
            );
        }
    }

    function _consumeBatchApproval(BatchApproval calldata approval, bytes calldata issuerSignature)
        private
        returns (bytes32 approvalDigest)
    {
        bytes32 structHash = keccak256(
            abi.encode(
                BATCH_APPROVAL_TYPEHASH,
                approval.issuerId,
                approval.batchIdHash,
                approval.merkleRoot,
                approval.schemaVersionHash,
                approval.leafCount,
                approval.treeVersion,
                approval.issuerKeyVersion,
                approval.approvalNonce,
                approval.deadline
            )
        );
        return _consumeApproval(
            approval.issuerId,
            approval.issuerKeyVersion,
            approval.approvalNonce,
            approval.deadline,
            structHash,
            issuerSignature
        );
    }

    function _consumeStatusApproval(StatusApproval calldata approval, bytes calldata issuerSignature)
        private
        returns (bytes32 approvalDigest)
    {
        bytes32 structHash = keccak256(
            abi.encode(
                STATUS_APPROVAL_TYPEHASH,
                approval.issuerId,
                approval.credentialIdHash,
                uint8(approval.action),
                approval.replacementCredentialIdHash,
                approval.effectiveAt,
                approval.issuerKeyVersion,
                approval.approvalNonce,
                approval.deadline
            )
        );
        return _consumeApproval(
            approval.issuerId,
            approval.issuerKeyVersion,
            approval.approvalNonce,
            approval.deadline,
            structHash,
            issuerSignature
        );
    }

    function _consumeApproval(
        bytes32 issuerId,
        uint64 issuerKeyVersion,
        uint64 approvalNonce,
        uint64 deadline,
        bytes32 structHash,
        bytes calldata issuerSignature
    ) private returns (bytes32 approvalDigest) {
        if (deadline < block.timestamp) revert ApprovalExpired(deadline);

        IssuerKey memory issuerKey = _activeIssuerKey(issuerId, issuerKeyVersion);
        approvalDigest = _hashTypedDataV4(structHash);
        if (usedApprovalDigest[approvalDigest]) revert ApprovalDigestAlreadyUsed(approvalDigest);
        if (_usedApprovalNonces[issuerId][approvalNonce]) {
            revert ApprovalNonceAlreadyUsed(issuerId, approvalNonce);
        }

        address recoveredSigner = ECDSA.recover(approvalDigest, issuerSignature);
        if (recoveredSigner != issuerKey.signer) {
            revert InvalidIssuerSignature(issuerId, issuerKeyVersion, recoveredSigner);
        }

        usedApprovalDigest[approvalDigest] = true;
        _usedApprovalNonces[issuerId][approvalNonce] = true;
    }

    function _activeIssuerKey(bytes32 issuerId, uint64 keyVersion)
        private
        view
        returns (IssuerKey memory issuerKey)
    {
        issuerKey = _issuerKeys[issuerId][keyVersion];
        if (issuerKey.signer == address(0)) revert IssuerKeyNotFound(issuerId, keyVersion);
        if (block.timestamp < issuerKey.validFrom || (issuerKey.validUntil != 0 && block.timestamp > issuerKey.validUntil)) {
            revert IssuerKeyNotActive(issuerId, keyVersion);
        }
        if (issuerKey.compromisedAt != 0 && block.timestamp >= issuerKey.compromisedAt) {
            revert IssuerKeyIsCompromised(issuerId, keyVersion);
        }
    }

    function _getIssuerKeyStorage(bytes32 issuerId, uint64 keyVersion)
        private
        view
        returns (IssuerKey storage issuerKey)
    {
        issuerKey = _issuerKeys[issuerId][keyVersion];
        if (issuerKey.signer == address(0)) revert IssuerKeyNotFound(issuerId, keyVersion);
    }

    function _currentTimestamp() private view returns (uint64) {
        return uint64(block.timestamp);
    }
}
