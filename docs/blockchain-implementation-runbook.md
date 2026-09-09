# Trekkey 블록체인 구현 및 실행 가이드

> 2026-09-08: 아래는 기존 Kaia 운용 가이드다. Sui 경로는
> [Sui 이식 계약](./sui-migration-2026-09-08.md), [Move 계약](../contracts-sui/README.md),
> [SDK 게이트웨이](../sui-gateway/README.md)를 사용한다. 이 변경은 운영 DB/체인 배포를 실행하지 않는다.

- 기준일: 2026-07-24
- 대상: Trekkey 백엔드 개발자, 학교 관리자, 배포 담당자
- 네트워크: Kaia Kairos `chainId=1001`
- 컨트랙트: `TrekkeyCredentialRegistryV1`

## 1. 현재 구현된 범위

| 영역 | 현재 상태 |
| --- | --- |
| Credential 원문 | Java에서 schema profile별 canonical JSON 생성 |
| 정규화·hash | Unicode NFC + RFC 8785 JCS + SHA-256 |
| 파일 증거 | 서버 계산 SHA-256을 정렬한 canonical manifest |
| 중복 발급 | source fingerprint 및 요청 내용 일치 검증 |
| Merkle Tree | OpenZeppelin `StandardMerkleTree`와 동일한 V1 구현 |
| 학교 승인 | EIP-712 `BatchApproval`, `StatusApproval` |
| 컨트랙트 | issuer key, batch root, revoke, supersede 저장 |
| Kaia 연결 | web3j 표준 EVM JSON-RPC adapter |
| 비동기 전송 | Transactional Outbox와 receipt/readback 검증 |
| 공개 검증 | canonical payload, local hash/proof, Kaia 상태 종합 판정 |
| 배포 도구 | Kairos 배포, 역할 설정, issuer 등록, 개발용 서명 도구 |

아직 팀 코드와 연결하지 않은 부분:

- `TEAM`, `SUBMISSION`, `AWARD` 확정 서비스에서 `CredentialIssuanceService.issue()` 호출
- 파일 업로드 stream의 SHA-256 계산 결과 전달
- QR 및 공개 검증 프론트 화면
- 학생 소유 Portable Credential Package
- 운영 KMS/HSM signer
- Kaia mainnet 배포

현재 백엔드에는 대회·팀·제출·수상 엔티티가 아직 합쳐지지 않았으므로, 임의 발급 HTTP API는 열지 않았다. 최종 업무 원장을 구현한 서비스가 확정된 snapshot만 내부 Java service로 전달해야 한다.

## 지금 담당자가 할 일

1. 팀원의 `TEAM`, `SUBMISSION`, `AWARD` 구현이 합쳐지면 각 확정 트랜잭션에서 `CredentialIssuanceService.issue()`를 호출한다.
2. 기존 `ORGANIZATION` 데이터에 `publicId`를 backfill하고 운영 migration에서 `NOT NULL + UNIQUE`로 고정한다.
3. Kairos용 deployer, 학교 issuer, Trekkey relayer 계정을 서로 다르게 만들고 테스트 KAIA는 deployer와 relayer에만 넣는다.
4. 컨트랙트를 Kairos에 배포하고 relayer 역할과 학교 issuer 공개 주소를 등록한다.
5. 백엔드를 먼저 `READ_ONLY`로 연결해 조회를 확인한 뒤, 한 worker 인스턴스만 `LOCAL_RELAYER`로 전환한다.
6. 테스트 Credential 1건을 발급하고 배치 서명, 앵커링, 공개 검증, 폐기까지 한 번 완주한다.
7. Kairos 운영 결과와 장애 복구 절차가 확인되기 전에는 mainnet으로 전환하지 않는다.

## 2. 키와 역할

| 주체 | 용도 | 보관 위치 |
| --- | --- | --- |
| Contract admin | 역할 부여와 운영 권한 관리 | 운영 multisig 권장 |
| Issuer key admin | 학교 issuer key 등록·폐기·탈취 표시 | 운영 multisig 또는 timelock |
| School issuer signer | EIP-712 발급·폐기 승인 | 학교 KMS/HSM 또는 외부 signer |
| Trekkey relayer | 승인된 트랜잭션 전송과 가스비 지불 | Trekkey KMS/HSM |
| 학생·검증자 | 서명·가스비 없음 | 지갑 불필요 |

학교 issuer와 Trekkey relayer를 같은 키로 사용하지 않는다. issuer는 “학교가 이 내용을 승인했다”를 증명하고, relayer는 이미 승인된 요청을 Kaia에 전달한다.

현재 `LOCAL_RELAYER` adapter는 개발·Kairos 통합 시험을 위해 환경변수에서 relayer private key를 읽으며, 런타임도 `chainId=1001`에서만 이 모드를 허용한다. Kaia mainnet 전송 전에는 `BlockchainAnchorPort`의 KMS/HSM adapter로 교체해야 한다.

학교 issuer의 온체인 `signer` 값은 KAIA를 보유한 계정 주소가 아니라 EIP-712 서명에서 복구되는 secp256k1 주소다. Kaia 역할 기반 키처럼 계정 주소와 서명 키가 분리된 주소를 그대로 등록하지 않는다. V1 표준 ECDSA 경로에서는 공개키로부터 계산한 EVM 주소를 등록하고, Kaia 계정 주소 자체를 issuer identity로 써야 한다면 `validateSender` 기반 별도 adapter와 컨트랙트 버전을 도입한다. 표준 web3j 트랜잭션을 보내는 Kairos relayer는 `AccountKeyLegacy` EOA를 사용한다.

학생이 트랜잭션을 보내지 않는 현재 구조에서는 Kaia native fee delegation이 필요하지 않다. Trekkey relayer 자체가 트랜잭션 발신자이자 가스비 납부자다. native fee delegation은 향후 학교나 학생 지갑이 직접 호출해야 할 때만 별도 adapter로 추가한다.

## 3. 최초 한 번 준비할 것

### 개발 도구

- JDK 21
- Node.js 20 또는 22 LTS
- MySQL
- Kairos KAIA를 가진 배포·relayer 계정
- 별도의 학교 issuer signer 계정

Node.js 25에서는 Hardhat 경고가 발생하므로 LTS 버전을 사용한다.

### 컨트랙트 설치와 검증

```bash
cd contracts
cp .env.example .env
npm install
npm run fixture:merkle
npm test
npx tsc --noEmit
```

`test/fixtures/merkle-v1.json`은 Java와 JavaScript가 같은 leaf, root, proof를 만드는 교차 언어 기준 파일이다. 이 파일과 `treeVersion=1` 규칙은 배포 후 임의로 바꾸지 않는다.

백엔드 검증:

```bash
./gradlew test
```

## 4. Kairos 배포

`contracts/.env`에 개발용 배포 키를 설정한다.

```dotenv
KAIROS_RPC_URL=https://public-en-kairos.node.kaia.io
DEPLOYER_PRIVATE_KEY=0x...
KAIASCAN_API_KEY=
```

배포와 소스 검증:

```bash
npm run deploy:kairos
npm run verify:kairos -- <REGISTRY_ADDRESS> <INITIAL_ADMIN_ADDRESS>
```

배포 출력의 contract address를 보관한다. 이후 백엔드의 `BLOCKCHAIN_CONTRACT_ADDRESS`와 EIP-712 `verifyingContract`는 반드시 이 주소여야 한다.

Kaia Foundation public RPC는 개발·시험용이다. 운영에서는 SLA와 rate limit이 명확한 provider를 사용하고, 장애 시 조회 전용 보조 provider를 둘 것을 권장한다.

## 5. Relayer 역할과 학교 issuer 등록

학교의 `ORGANIZATION.publicId`가 먼저 DB에 고정되어 있어야 한다. 표시 이름이나 내부 bigint ID가 아니라 이 UUID가 issuer ID 계산의 입력이다.

`contracts/.env`:

```dotenv
REGISTRY_ADDRESS=0x...
ISSUER_PUBLIC_ID=<organization.publicId>
ISSUER_KEY_VERSION=1
ISSUER_SIGNER_ADDRESS=0x...
RELAYER_ADDRESS=0x...
```

설정:

```bash
npm run configure:kairos
```

이 스크립트는 다음을 수행한다.

1. `ISSUER_PUBLIC_ID`로 domain-separated `issuerId` 계산
2. relayer에 `RELAYER_ROLE` 부여
3. `issuerId + keyVersion`에 학교 signer 주소 등록
4. 이미 같은 값이면 멱등하게 종료
5. 같은 key version에 다른 signer가 있으면 실패

운영에서는 초기 deployer 권한을 그대로 두지 않는다. multisig/timelock에 역할을 넘긴 뒤 deployer의 운영 역할을 제거한다.

## 6. Spring Boot 설정

기본값은 블록체인 기능이 완전히 꺼진 `DISABLED`다.

```dotenv
BLOCKCHAIN_ANCHORING_MODE=LOCAL_RELAYER
BLOCKCHAIN_CHAIN_ID=1001
BLOCKCHAIN_RPC_URL=https://public-en-kairos.node.kaia.io
BLOCKCHAIN_CONTRACT_ADDRESS=0x...
BLOCKCHAIN_RUNTIME_CODE_HASH=0x...
BLOCKCHAIN_CONTRACT_VERSION=1
BLOCKCHAIN_TREE_VERSION=1
BLOCKCHAIN_BATCH_SIZE=100
BLOCKCHAIN_APPROVAL_TTL=15m
BLOCKCHAIN_WORKER_ENABLED=true
BLOCKCHAIN_WORKER_CLAIM_SIZE=10
BLOCKCHAIN_WORKER_MAX_ATTEMPTS=8
BLOCKCHAIN_OUTBOX_LEASE_TIMEOUT=1m
BLOCKCHAIN_RECEIPT_POLLING_INTERVAL=2s
BLOCKCHAIN_RECEIPT_TIMEOUT=2m
```

위 값은 비밀이 아닌 배포 설정이다. GitHub `production` Environment Variables에 저장하며, DB 비밀번호와 JWT 키가 들어 있는 `ENV_FILE` Secret과 분리한다.

현재 Kairos 배포값은 [`contracts/deployments/kairos-1001.json`](../contracts/deployments/kairos-1001.json)을 원본으로 사용한다.

```dotenv
BLOCKCHAIN_ANCHORING_MODE=LOCAL_RELAYER
BLOCKCHAIN_CHAIN_ID=1001
BLOCKCHAIN_RPC_URL=https://public-en-kairos.node.kaia.io
BLOCKCHAIN_CONTRACT_ADDRESS=0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117
BLOCKCHAIN_RUNTIME_CODE_HASH=0x6bdcd078a99c833e1e6126d71954b570fb7bfb4afe4720fc4a438009039571a2
BLOCKCHAIN_CONTRACT_VERSION=1
BLOCKCHAIN_TREE_VERSION=1
BLOCKCHAIN_BATCH_SIZE=100
BLOCKCHAIN_APPROVAL_TTL=15m
BLOCKCHAIN_WORKER_ENABLED=true
BLOCKCHAIN_WORKER_CLAIM_SIZE=10
BLOCKCHAIN_WORKER_MAX_ATTEMPTS=8
BLOCKCHAIN_OUTBOX_LEASE_TIMEOUT=1m
BLOCKCHAIN_RECEIPT_POLLING_INTERVAL=2s
BLOCKCHAIN_RECEIPT_TIMEOUT=2m
```

relayer private key는 GitHub에 저장하지 않는다. EC2 한 곳에서만 다음 형식의 별도 파일로 주입한다.

```text
/etc/trekkey/relayer.env
owner: root:root
mode: 600
content: BLOCKCHAIN_RELAYER_PRIVATE_KEY=<32-byte hex private key>
```

키 값을 터미널 인자, 문서, Git 로그에 붙여 넣지 않는다. 로컬 Keychain에서 표준입력으로 EC2에 전송하고, 배포 워크플로는 파일 존재 여부·권한·형식만 검사한다. 컨테이너 환경변수 주입은 Kairos 통합 시험용이며, Kaia mainnet 전환 전에는 `BlockchainAnchorPort`의 KMS/HSM signer adapter로 교체한다.

기존 MySQL DB에서 worker를 켜기 전에
[`2026-08-10-expand-signed-raw-transaction.sql`](migrations/2026-08-10-expand-signed-raw-transaction.sql)을
실행한다. `anc_chain_transaction.signed_raw_transaction`이 `TINYBLOB`이면
255바이트를 넘는 실제 EVM raw transaction을 브로드캐스트 전에 저장할 수 없다.
V1은 `BLOB`으로 고정하고 MySQL 통합 테스트에서 1 KiB 저장을 검증한다.

모드:

| 모드 | 동작 |
| --- | --- |
| `DISABLED` | 체인 조회와 전송 모두 차단 |
| `READ_ONLY` | 공개 검증과 issuer 조회만 허용 |
| `LOCAL_RELAYER` | Kairos(`1001`)에서만 조회와 개발용 로컬 relayer 전송 허용 |

`BLOCKCHAIN_WORKER_ENABLED=true`는 실제 트랜잭션을 보낼 인스턴스에만 설정한다. 백엔드 수평 확장 시 모든 API 인스턴스에 무조건 켜지 않는다.

## 7. 학교 issuer key 동기화

컨트랙트에 issuer를 등록한 뒤 학교 관리자 JWT로 백엔드 원장에 공개 주소를 동기화한다.

```bash
curl -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"signerRef":"school-kms:key-1"}' \
  http://localhost:8080/api/admin/blockchain/issuer-keys/1/sync
```

`signerRef`는 private key가 아니다. 외부 signer에서 키를 찾기 위한 관리용 참조 문자열이다.

키 회전 시에는 새 `keyVersion`을 온체인에 등록하고 동기화한 뒤 새 배치부터 그 버전을 사용한다. 정상 회전은 현재 시각으로 이전 키를 종료하므로 그 전에 앵커된 batch의 검증 결과는 유지된다. 침해가 뒤늦게 발견되면 `compromisedAt`을 실제 사고 시각으로 소급 기록할 수 있고, 이 경우 그 시각 이후 해당 키로 기록된 batch와 상태 변경은 `ISSUER_INVALID`가 된다. 온체인 root 자체는 삭제되거나 변경되지 않는다.

## 8. 업무 확정 서비스가 호출할 발급 경계

업무 팀원이 구현할 확정 서비스는 아래 조건을 먼저 보장한다.

```text
PARTICIPATION -> 팀 명단 확정
WORK          -> 제출 확정 + 모든 파일의 서버 계산 SHA-256 준비
AWARD         -> 수상 확정 + 발급 당시 팀원 명단 준비
```

그 다음 내부 Java 경계인 `CredentialIssuanceService.issue(command)`를 호출한다.

`CredentialIssueCommand`에 들어갈 값:

- 학교 ID와 Credential 번호
- type과 고정 schema profile
- 확정된 source의 public ID, finalized time, snapshot JSON
- 팀 subject 한 명과 사용자 subject 전원
- 파일 이름, MIME type, 크기, 서버 계산 SHA-256
- 발급·만료 시각

호출자는 브라우저가 보낸 hash를 그대로 전달하면 안 된다. 파일 저장 stream을 서버가 읽으면서 SHA-256을 계산해야 한다.

동일 source fingerprint의 재시도는 기존 Credential을 반환한다. 같은 source인데 Credential 번호, 시각, file manifest가 다르면 정정 절차 없이 덮어쓰지 않고 `409`로 거부한다.

## 9. 배치 생성과 학교 승인

### 배치 생성

```bash
curl -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "schemaProfileId":"trekkey:award:v1:jcs-rfc8785:unicode-nfc-1",
    "keyVersion":1
  }' \
  http://localhost:8080/api/admin/blockchain/batches
```

배치는 같은 학교와 schema profile의 `READY` Credential만 묶는다. 응답의 `publicId`를 다음 단계에서 사용한다.

### EIP-712 typed data 조회

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  http://localhost:8080/api/admin/blockchain/batches/$BATCH_PUBLIC_ID/approval \
  | jq -r '.data.typedDataJson' > /tmp/trekkey-batch-approval.json
```

학교 signer가 이 JSON을 검토하고 서명한다. 서명 대상에는 root, schema hash, leaf count, tree version, key version, nonce, deadline, chain ID, contract address가 모두 포함된다.

### Kairos 개발용 서명

운영에서는 이 명령 대신 학교 KMS/HSM 또는 지갑 signer를 사용한다.

```bash
cd contracts
ISSUER_PRIVATE_KEY=0x... \
TYPED_DATA_FILE=/tmp/trekkey-batch-approval.json \
npm run sign:approval
```

출력된 digest가 API 응답 digest와 같은지 확인한다.

서명은 `r || s || v` 65바이트 형식이어야 한다. 백엔드는 `v=0/1`을 `27/28`로 정규화하고, Solidity OpenZeppelin `ECDSA`와 동일하게 high-s 서명을 거부한다.

### 서명 제출

```bash
curl -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"signatureHex\":\"$ISSUER_SIGNATURE\"}" \
  http://localhost:8080/api/admin/blockchain/batches/$BATCH_PUBLIC_ID/approval
```

서명이 학교의 등록된 공개 주소와 일치해야 Outbox가 생성된다. 학생은 이 과정에 참여하지 않는다.

승인 deadline이 지난 경우 기존 typed data에 다시 서명하지 않는다.

```text
POST /api/admin/blockchain/batches/{batchPublicId}/approval/renew
```

- 아직 서명하지 않은 `SEALED` 배치는 새 nonce와 deadline을 발급한다.
- 전송 작업이 `FAILED + DEAD`인 배치는 먼저 온체인 batch 존재 여부를 조회한다.
- 온체인에 없을 때만 새 승인을 발급하며, 새 서명이 검증된 뒤 기존 transaction/outbox를 재사용한다.
- `PREPARED`, `SUBMITTED`, `UNKNOWN`은 결과가 불명확할 수 있으므로 승인 갱신 대상이 아니다.

로컬 transaction은 실패했지만 온체인 batch가 정확히 존재하면 갱신 대신 아래 API를 사용한다.

```text
POST /api/admin/blockchain/batches/{batchPublicId}/reconcile
```

issuer, root, schema hash, leaf count, tree version, key version이 모두 같을 때만 batch와 Credential을 `ANCHORED`로 수렴시킨다. 실패한 transaction과 outbox는 감사 증거로 그대로 남는다.

## 10. Anchor worker와 검증

worker는 다음 순서로 처리한다.

```text
Outbox claim
-> EIP-712 승인과 exact calldata 준비
-> signed raw transaction, nonce, tx hash를 DB에 먼저 고정
-> DB commit
-> 같은 raw transaction을 Kaia에 broadcast
-> receipt와 예상 event 확인
-> getBatch/status readback 비교
-> batch와 Credential을 ANCHORED로 확정
```

전송 결과가 불명확하면 새 트랜잭션을 만들지 않고, 이미 저장한 tx hash의 receipt를 조회한다. 미확정 상태가 지속되면 동일 raw transaction만 `receipt-timeout` 간격으로 재방송하므로 tx hash와 nonce는 바뀌지 않는다. 성공 receipt만 보는 것이 아니라 contract address, 예상 event, on-chain state가 DB 증거와 같은지 다시 확인한다.

공개 검증:

```bash
curl -sS \
  http://localhost:8080/api/public/credentials/$CREDENTIAL_PUBLIC_ID \
  | jq
```

주요 상태:

| 상태 | 의미 |
| --- | --- |
| `VALID` | canonical claims, hash, proof, issuer key, Kaia 상태가 모두 정상 |
| `PENDING` | 아직 온체인 확정 전 |
| `REVOKED` | 학교가 폐기 |
| `SUPERSEDED` | 새 Credential로 대체 |
| `EXPIRED` | Credential 자체 만료 |
| `TAMPERED` | canonical claims 또는 Merkle 증거 불일치 |
| `ANCHOR_NOT_FOUND` | DB는 확정됐지만 온체인 batch가 없음 |
| `ISSUER_INVALID` | 발급 시점 issuer key가 유효하지 않음 |
| `RPC_UNAVAILABLE` | 체인을 조회할 수 없어 위조 여부를 단정하지 않음 |
| `BLOCKCHAIN_CONFIGURATION_ERROR` | chain ID, contract 주소 또는 ABI 설정이 실제 체인과 맞지 않음 |
| `SCHEMA_UNSUPPORTED` | 현재 verifier가 schema/tree 규칙을 모름 |

응답의 `issuerId`, `credentialIdHash`, `schemaVersionHash`, `contentHash`, `fileManifestHash`, `leafHash`, `batchIdHash`, `merkleRoot`, `treeVersion`, `merkleProof`로 제3자가 Merkle membership을 재검산할 수 있다. 비공개 subject가 포함된 canonical 원문 전체는 공개 API에 노출하지 않는다.

외부 사용자는 Trekkey 계정이나 Kaia 지갑 없이 프론트의 공개 경로로
검증한다.

```text
/verify                         검증 코드 또는 QR 링크 입력
/verify/{credentialPublicId}    QR에서 바로 여는 검증 결과
```

결과 화면은 먼저 발급기관, 증명 종류, 대회, 팀, 작품, 상격, 공개 대상자와
현재 효력을 일반 용어로 보여준다. 이 표시는 검증을 통과한 canonical
Credential의 `source.snapshot`과 `subjects`에서만 만들며, 현재 업무
테이블을 다시 조회해 덮어쓰지 않는다. 따라서 화면에 보이는 수상 내용도
`contentHash`와 Merkle root로 보호된다. 해시, proof, chain ID, contract와
transaction hash는 제3자 재검산을 위한 기술 상세로 접어서 제공한다.

공개 API와 화면에는 학번, 이메일, 비공개 subject, canonical 원문 전체를
노출하지 않는다. 학번 기반 전체 이력 조회는 본인 또는 학교 관리자 인증이
필요한 별도 기능으로 유지한다.

## 11. 폐기와 대체

폐기 요청:

```bash
curl -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "action":"REVOKE",
    "issuerKeyVersion":1,
    "reasonCode":"ISSUED_IN_ERROR",
    "reasonDetail":"학교 확인 후 오발급 폐기"
  }' \
  http://localhost:8080/api/admin/blockchain/credentials/$CREDENTIAL_PUBLIC_ID/status-events
```

대체 요청:

```json
{
  "action": "SUPERSEDE",
  "replacementCredentialPublicId": "new-credential-public-id",
  "issuerKeyVersion": 1,
  "reasonCode": "CORRECTED",
  "reasonDetail": "수상명 정정"
}
```

대체 Credential은 먼저 별도 batch에서 `ANCHORED`되어야 한다. 이후 status approval typed data를 조회·서명·제출한다.

```text
GET  /api/admin/blockchain/status-events/{statusEventId}/approval
POST /api/admin/blockchain/status-events/{statusEventId}/approval
```

기존 canonical Credential을 UPDATE하지 않는다. 정정은 새 Credential을 발급한 뒤 기존 것을 `SUPERSEDED`로 표시한다.

상태 승인도 만료된 경우 같은 원칙으로 갱신한다.

```text
POST /api/admin/blockchain/status-events/{statusEventId}/approval/renew
```

갱신 전에 `getCredentialStatus`를 조회하므로 이미 온체인에서 처리된 폐기·대체를 새 nonce로 다시 보내지 않는다.

이미 온체인 상태가 정확히 존재하면 아래 API로 Credential 상태만 수렴시킨다.

```text
POST /api/admin/blockchain/status-events/{statusEventId}/reconcile
```

action, effective time, issuer key version, replacement hash가 모두 일치해야 한다.

## 12. 배포 전 필수 확인

- [ ] `ORGANIZATION.publicId` 기존 데이터 backfill 및 `NOT NULL + UNIQUE` migration
- [ ] 대회·팀·제출·수상 FK와 tenant 소속 검증 연결
- [ ] Kairos contract source 검증
- [ ] Java와 OpenZeppelin Merkle fixture 교차 테스트
- [ ] 학교 issuer와 relayer 키 분리
- [ ] relayer key가 EC2 `/etc/trekkey/relayer.env`에만 존재하고 권한이 `600`인지 확인
- [ ] Registry runtime code hash가 배포 manifest와 일치하는지 확인
- [ ] 승인 nonce·deadline·chain ID·contract address 확인
- [ ] relayer 잔액 및 가스 예산 알림
- [ ] Outbox backlog, `UNKNOWN`, `DEAD` 알림
- [ ] `FAILED + DEAD` 승인 갱신 및 온체인 readback 운영 절차
- [ ] MySQL 백업·복구 훈련
- [ ] 객체 저장소 versioning과 파일 hash 재검증
- [ ] KMS/HSM adapter 및 운영 multisig/timelock
- [ ] Mainnet 전 Hardhat 3 이전 및 `npm audit` 고위험 항목 0건 또는 보안 승인된 예외 목록
- [ ] Kairos 통합 시험 후에만 mainnet `chainId=8217` 검토

## 13. 포트폴리오 설명

Trekkey는 인증서 원문과 개인정보를 퍼블릭 블록체인에 저장하지 않는다. 학교가 확정한 참여·작품·수상 데이터를 canonical Credential로 고정하고, 여러 Credential의 Merkle root만 Kaia에 기록한다.

학교 issuer가 EIP-712로 root와 nonce, deadline, chain, contract를 승인하고 Trekkey relayer가 가스비를 부담한다. 따라서 학생은 지갑이나 코인 없이도 QR 링크만으로 증빙을 검증할 수 있다.

백엔드는 업무 트랜잭션과 RPC를 직접 묶지 않고 Outbox로 분리한다. signed raw transaction과 tx hash를 broadcast 전에 저장해 응답 유실과 프로세스 장애에도 같은 트랜잭션을 추적한다. 공개 검증은 DB의 표시용 컬럼이 아니라 온체인 root에 포함된 canonical Credential에서 claims를 읽고, hash·proof·issuer key·폐기 상태를 단계별로 비교한다.

블록체인은 학교가 앵커링 전에 허위 결과를 입력했는지 판정하지 않는다. 대신 학교가 승인한 증빙이 앵커링 뒤에 바뀌었는지, 폐기됐는지, 더 최신 증빙으로 대체됐는지를 외부에서 독립적으로 확인하게 한다.
