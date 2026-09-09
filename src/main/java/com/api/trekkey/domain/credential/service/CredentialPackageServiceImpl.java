package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.entity.AncBatch;
import com.api.trekkey.domain.credential.repository.AncBatchRepository;
import com.api.trekkey.domain.credential.service.dto.CredentialPackageFile;
import com.api.trekkey.domain.credential.service.dto.CredentialVerificationView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * 익명 공개 검증용 package.
 *
 * <p>공개 검증 응답과 같은 disclosure boundary를 유지한다. canonical Credential과 file manifest는
 * 비공개 subject·원본 파일 메타데이터를 포함할 수 있으므로 이 package에 넣지 않는다. 학생 본인이
 * 소유하는 전체 Portable Credential Package는 별도의 인증·소유권 경계로 제공해야 한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialPackageServiceImpl implements CredentialPackageService {

    private final CredentialVerificationService credentialVerificationService;
    private final AncBatchRepository batchRepository;
    private final CredentialCertificateService credentialCertificateService;
    private final ObjectMapper objectMapper;

    @Override
    public CredentialPackageFile buildPublicPackage(String credentialPublicId) {
        // 검증 서비스가 존재 여부와 hash·proof 재계산을 확인하고 공개 허용 필드만 반환한다.
        CredentialVerificationView view = credentialVerificationService.verify(credentialPublicId);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            addEntry(zip, "public-credential.json", writeJson(publicCredentialJson(view)));
            addEntry(zip, "merkle-proof.json", writeJson(merkleProofJson(view)));
            addEntry(zip, "anchor.json", writeJson(anchorJson(view)));
            addEntry(zip, "issuer-approval.json", writeJson(issuerApprovalJson(view)));
            addEntry(zip, "status.json", writeJson(statusJson(view)));
            addEntry(zip, "rendered-certificate.pdf",
                    credentialCertificateService.renderCertificate(credentialPublicId).zipBytes());
            addEntry(zip, "README.txt", readme(view).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return new CredentialPackageFile(
                "trekkey-public-verification-" + view.credentialNo() + ".zip",
                buffer.toByteArray());
    }

    //======= 헬퍼 메서드 ==========

    private ObjectNode publicCredentialJson(CredentialVerificationView view) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("disclosure", "ISSUANCE_PUBLIC_SUMMARY");
        node.put("credentialPublicId", view.credentialPublicId());
        node.put("credentialNo", view.credentialNo());
        node.put("credentialType", view.credentialType() == null ? null : view.credentialType().name());
        node.put("schemaProfileId", view.schemaProfileId());
        node.put("issuerPublicId", view.issuerPublicId());
        node.put("issuerName", view.issuerName());
        node.put("issuedAt", view.issuedAt() == null ? null : view.issuedAt().toString());
        node.put("expiresAt", view.expiresAt() == null ? null : view.expiresAt().toString());
        node.set("publicDetails", publicDetailsJson(view.publicDetails()));

        ArrayNode subjects = node.putArray("publicSubjects");
        view.publicSubjects().forEach(subject -> subjects.add(publicSubjectJson(subject)));
        node.put("verificationStatus", view.verificationStatus().name());
        node.put("replacementCredentialPublicId", view.replacementCredentialPublicId());
        node.put("notice", "발급 당시 PUBLIC으로 지정된 요약입니다. 현재 공유 동의를 별도 검증한 결과가 아니며 canonical 원문 또는 contentHash의 preimage가 아닙니다.");
        return node;
    }

    private ObjectNode publicDetailsJson(CredentialVerificationView.PublicDetails details) {
        ObjectNode node = objectMapper.createObjectNode();
        if (details == null) {
            return node;
        }
        node.put("sourceType", details.sourceType());
        node.put("sourcePublicId", details.sourcePublicId());
        node.put("finalizedAt", details.finalizedAt() == null ? null : details.finalizedAt().toString());
        node.put("contestTitle", details.contestTitle());
        node.put("teamName", details.teamName());
        node.put("submissionTitle", details.submissionTitle());
        node.put("prize", details.prize());
        if (details.awardRankNo() == null) {
            node.putNull("awardRankNo");
        } else {
            node.put("awardRankNo", details.awardRankNo());
        }
        return node;
    }

    private ObjectNode publicSubjectJson(CredentialVerificationView.PublicSubject subject) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("subjectRef", subject.subjectRef());
        node.put("subjectType", subject.subjectType());
        node.put("displayName", subject.displayName());
        node.put("major", subject.major());
        node.put("roleCode", subject.roleCode());
        return node;
    }

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
        node.put("transactionHash", view.evidence().transactionHash());
        node.put("blockNumber", view.evidence().blockNumber());
        node.put("merkleRoot", view.evidence().merkleRoot());
        if (view.evidence().blockchain() != null) node.set("blockchain", objectMapper.valueToTree(view.evidence().blockchain()));
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
        node.put("approvalDeadline", batch.getApprovalDeadline() == null ? null : batch.getApprovalDeadline().toString());
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

    private String readme(CredentialVerificationView view) {
        return """
                Trekkey Public Verification Package
                ====================================
                credentialNo: %s

                이 패키지는 익명 공개 검증 페이지에 표시되는 범위의 요약과 암호학적 근거를 보관합니다.
                개인정보가 포함될 수 있는 canonical Credential 원문과 file manifest는 포함하지 않습니다.

                - public-credential.json 발급 당시 PUBLIC 지정 요약 (현재 동의 확인·canonical 원문 아님)
                - merkle-proof.json    Merkle leaf·proof·root (OpenZeppelin StandardMerkleTree 호환)
                - anchor.json          기록된 네트워크 좌표와 blockchain 메타데이터
                - issuer-approval.json 발급 학교의 65-byte secp256k1 배치 승인 서명
                - status.json          패키지 생성 시점의 검증 상태
                - rendered-certificate.pdf 사람용 표시물 (cryptographic source of truth 아님)

                이 공개 package만으로는 숨겨진 canonical 원문을 재구성하거나 contentHash를 다시 계산할 수
                없습니다. 제공된 leaf와 proof로 batch 포함 여부를 확인하고, anchor.json의 컨트랙트에서
                root·issuer·폐기·대체 상태를 조회할 수 있습니다. 전체 원문 독립 검증 package는 본인 인증과
                소유권 확인이 적용된 별도 경계에서 제공해야 합니다.

                blockchain.provider=SUI인 경우 chainId=0은 EVM chain ID가 없다는 호환 표기입니다.
                contractAddress는 원본 Move package ID, transactionHash는 Sui base58 digest,
                blockNumber는 checkpoint sequence입니다. registryObjectId와 실제 chainIdentifier는
                blockchain 메타데이터를 사용합니다. 기관 승인은 TREKKEY_SUI_APPROVAL_V1이며
                EIP-712 지갑 서명이나 Sui 트랜잭션 서명이 아닙니다. KAIA 기록은 기존 EIP-712를 유지합니다.
                이 파일에 포함된 키·트랜잭션 좌표만으로 현재 유효성을 단정하지 말고 상태를 다시 조회하세요.
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
