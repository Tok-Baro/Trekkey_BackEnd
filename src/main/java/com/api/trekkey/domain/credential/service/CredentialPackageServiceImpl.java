package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portable Credential Package (erd-mvp §12·§13).
 * 학생이 학교 시스템과 무관하게 보관·재검증할 수 있는 증빙 묶음 —
 * canonical 원문, file manifest, Merkle proof, 앵커링 좌표, 발급자 승인, 현재 상태를 zip으로 담는다.
 * PDF 표시물은 §12 정의상 cryptographic source of truth가 아니므로 후속(상장 템플릿 확정 후)으로 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialPackageServiceImpl implements CredentialPackageService {

    private final CredentialVerificationService credentialVerificationService;
    private final AncCredentialRepository credentialRepository;
    private final AncBatchRepository batchRepository;
    private final ObjectMapper objectMapper;

    @Override
    public CredentialPackageFile buildPackage(String credentialPublicId) {
        AncCredential credential = credentialRepository.findByPublicId(credentialPublicId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.CREDENTIAL_NOT_FOUND));
        requirePublicDisclosure(credential);
        //검증 서비스가 hash·proof 재계산까지 끝낸 상태를 그대로 담는다 (§9와 동일 근거)
        CredentialVerificationView view = credentialVerificationService.verify(credentialPublicId);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            //§12 구성 파일 — credential.json은 발급 시점 canonical 원문 그대로
            addEntry(zip, "credential.json", credential.getCanonicalBytes());
            addEntry(zip, "file-manifest.json", credential.getFileManifestCanonicalBytes());
            addEntry(zip, "merkle-proof.json", writeJson(merkleProofJson(view)));
            addEntry(zip, "anchor.json", writeJson(anchorJson(view)));
            addEntry(zip, "issuer-approval.json", writeJson(issuerApprovalJson(view)));
            addEntry(zip, "status.json", writeJson(statusJson(view)));
            addEntry(zip, "README.txt", readme(view).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        String fileId = view.credentialNo() == null ? credentialPublicId : view.credentialNo();
        return new CredentialPackageFile(
                "trekkey-credential-" + fileId + ".zip",
                buffer.toByteArray());
    }

    //======= 헬퍼 메서드 ==========

    private ObjectNode merkleProofJson(CredentialVerificationView view) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("leafHash", view.evidence().leafHash());
        node.put("merkleRoot", view.evidence().merkleRoot());
        view.evidence().merkleProof().forEach(node.withArray("merkleProof")::add);
        node.put("treeVersion", view.evidence().treeVersion() == null ? null : String.valueOf(view.evidence().treeVersion()));
        node.put("batchPublicId", view.evidence().batchPublicId());
        node.put("batchIdHash", view.evidence().batchIdHash());
        node.put("issuerId", view.evidence().issuerId());
        node.put("credentialIdHash", view.evidence().credentialIdHash());
        node.put("schemaVersionHash", view.evidence().schemaVersionHash());
        node.put("contentHash", view.evidence().contentHash());
        node.put("fileManifestHash", view.evidence().fileManifestHash());
        return node;
    }

    private ObjectNode anchorJson(CredentialVerificationView view) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("chainId", view.evidence().chainId());
        node.put("contractAddress", view.evidence().contractAddress());
        node.put("contractVersion", view.evidence().contractVersion());
        node.put("transactionHash", view.evidence().transactionHash());
        node.put("blockNumber", view.evidence().blockNumber());
        node.put("merkleRoot", view.evidence().merkleRoot());
        return node;
    }

    private ObjectNode issuerApprovalJson(CredentialVerificationView view) {
        ObjectNode node = objectMapper.createObjectNode();
        String batchPublicId = view.evidence().batchPublicId();
        if (batchPublicId == null) {
            //아직 배치에 묶이기 전 — 앵커링 후 패키지를 다시 내려받으면 채워진다
            node.put("state", "NOT_BATCHED");
            return node;
        }
        AncBatch batch = batchRepository.findByPublicId(batchPublicId).orElse(null);
        if (batch == null || batch.getIssuerSignature() == null) {
            node.put("state", "NOT_SIGNED");
            return node;
        }
        node.put("state", "SIGNED");
        node.put("approvalPayload", batch.getApprovalPayloadJson());
        node.put("approvalNonce", batch.getApprovalNonce());
        node.put("approvalDeadline", batch.getApprovalDeadline().toString());
        node.put("issuerSignature", toHex(batch.getIssuerSignature()));
        return node;
    }

    private ObjectNode statusJson(CredentialVerificationView view) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("credentialPublicId", view.credentialPublicId());
        node.put("credentialNo", view.credentialNo());
        node.put("credentialType", view.credentialType() == null ? null : view.credentialType().name());
        node.put("verificationStatus", view.verificationStatus().name());
        node.put("issuerName", view.issuerName());
        node.put("issuerPublicId", view.issuerPublicId());
        node.put("schemaProfileId", view.schemaProfileId());
        node.put("issuedAt", view.issuedAt() == null ? null : view.issuedAt().toString());
        node.put("replacementCredentialPublicId", view.replacementCredentialPublicId());
        return node;
    }

    private void requirePublicDisclosure(AncCredential credential) {
        try {
            JsonNode subjects = objectMapper.readTree(credential.getCanonicalBytes()).get("subjects");
            if (subjects == null || !subjects.isArray() || subjects.isEmpty()) {
                throw new CustomException(CredentialErrorResponseCode.CREDENTIAL_PACKAGE_NOT_PUBLIC);
            }
            for (JsonNode subject : subjects) {
                JsonNode disclosureClass = subject.get("disclosureClass");
                if (disclosureClass == null
                        || !disclosureClass.isTextual()
                        || !"PUBLIC".equals(disclosureClass.textValue())) {
                    throw new CustomException(CredentialErrorResponseCode.CREDENTIAL_PACKAGE_NOT_PUBLIC);
                }
            }
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CustomException(CredentialErrorResponseCode.CREDENTIAL_PACKAGE_NOT_PUBLIC);
        }
    }

    private String readme(CredentialVerificationView view) {
        return """
                Trekkey Portable Credential Package
                ===================================
                credentialNo: %s

                이 패키지는 학교 시스템과 무관하게 Credential을 재검증할 수 있는 증빙 묶음입니다.

                - credential.json      발급 시점에 고정된 canonical 원문 (RFC 8785 JCS)
                - file-manifest.json   제출 파일 SHA-256 목록의 canonical 원문
                - merkle-proof.json    Merkle leaf·proof·root (OpenZeppelin StandardMerkleTree 호환)
                - anchor.json          Kaia 앵커링 좌표 (chainId, contract, tx)
                - issuer-approval.json 발급 학교의 EIP-712 배치 승인 서명
                - status.json          패키지 생성 시점의 검증 상태

                검증 방법: credential.json을 SHA-256 해시하면 merkle-proof.json의 contentHash와
                일치해야 하고, leaf 재계산 후 proof를 따라가면 merkleRoot가 나오며,
                그 root는 anchor.json의 컨트랙트에 기록된 값과 같아야 합니다.
                """.formatted(view.credentialNo());
    }

    private byte[] writeJson(ObjectNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content == null ? new byte[0] : content);
        zip.closeEntry();
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder("0x");
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
