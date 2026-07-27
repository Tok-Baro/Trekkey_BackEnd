<aside>

**최종 구현 기준 · 2026-07-21**

이 페이지를 Trekkey 업무 DB와 Kaia 앵커링의 팀 공통 기준으로 사용한다. 기존 v1·v2 문서는 의사결정 이력이며, 구현이 충돌하면 이 페이지와 최종 ERD를 먼저 갱신한다.

</aside>

## 1. 한 줄 결론

Trekkey는 학교가 확정한 **참여·작품·수상 Credential 원문과 개인정보를 SQL 및 객체 저장소에 보존**하고, 여러 Credential을 Merkle Tree로 묶어 **root만 Kaia에 앵커링**한다.

> 블록체인은 학교가 처음 입력한 사실의 현실적 진실성을 판정하지 않는다. 학교가 승인한 특정 시점의 Credential이 앵커링 이후 변경되지 않았고, 현재 폐기 또는 대체되지 않았는지를 검증한다.
> 

### 해결하려는 문제

- 졸업 이후 학교 시스템에서 대회 참여·작품·수상 기록이 사라지는 문제
- 팀 상장과 구성원 관계가 끊겨 개인 이력으로 조회되지 않는 문제
- 발급자가 나중에 원문을 조용히 바꾸더라도 외부에서 알기 어려운 문제
- 인증서마다 트랜잭션을 보내면 발급량에 비례해 비용이 증가하는 문제

## 2. 범위와 제외 범위

| 구분 | 현재 MVP | 후속 확장 |
| --- | --- | --- |
| Credential | 참여, 최종 작품, 수상 | 졸업요건, 학적 이력, 독립 작품 |
| 제출물 | 팀당 최종 제출물 한 건, 마감 전 덮어쓰기 | 제출 버전 원장이 실제로 필요할 때 추가 |
| 폐기 | Credential별 REVOKED·SUPERSEDED | 서명된 batch 전체 폐기 |
| 배포 | 중앙 Spring Boot·MySQL multi-tenant | 학교별 Source·Signer Adapter |

## 3. 시스템 전체 구조

```mermaid
flowchart LR
    subgraph actors ["사용자"]
        student["학생"]
        admin["학교 관리자"]
        judge["심사위원"]
        verifier["외부 검증자"]
    end

    subgraph client ["클라이언트"]
        web["Trekkey Web"]
        verifyPage["공개 검증 페이지"]
    end

    subgraph backend ["Trekkey Off-chain"]
        api["Spring Boot API"]
        credentialWorker["Credential Worker"]
        batchWorker["Merkle Batch Worker"]
        anchorWorker["Anchor Worker"]
        mysql[("MySQL")]
        objectStore[("Object Storage")]
        signer["학교 Issuer Signer"]
        relayer["Trekkey Relayer"]
    end

    subgraph chain ["Kaia On-chain"]
        registry["TrekkeyCredentialRegistryV1"]
    end

    student --> web
    admin --> web
    judge --> web
    verifier --> verifyPage
    web --> api
    verifyPage --> api
    api --> mysql
    api --> objectStore
    mysql -.-> credentialWorker
    credentialWorker --> mysql
    mysql -.-> batchWorker
    batchWorker --> mysql
    batchWorker --> signer
    signer --> batchWorker
    mysql -.-> anchorWorker
    anchorWorker --> relayer
    relayer --> registry
    registry --> anchorWorker
    api --> registry
```

### 책임 경계

| 영역 | 책임 |
| --- | --- |
| 업무 SQL | 학교, 사용자, 대회, 팀원, 제출, 심사, 공식 판정, 수상 |
| 앵커링 SQL | Credential snapshot, canonical bytes, hash, proof, 승인 서명, 트랜잭션 영수증, Outbox |
| Object Storage | 제출 원본, 인증서 PDF, Portable Credential Package |
| Kaia | Issuer key, batch root, schema·tree version, Credential 폐기·대체 상태 |

## 4. 전체 통합 ERD

전체 ERD는 **업무 SQL 16개 + 인증·관리자 3개(USER 확장, ADMIN_INVITATION, ADMIN_AUDIT_LOG, REFRESH_TOKEN) + Credential·앵커링 SQL 9개**, 총 28개 테이블로 구성한다. 컬럼까지 포함한 전체 원본은 repo의 [`docs/trekkey-unified-erd.mmd`](./trekkey-unified-erd.mmd)가 정본이다.

### 엔터티 묶음

| 도메인 | 테이블 | 역할 |
| --- | --- | --- |
| 기관·사용자 | ORGANIZATION, USER | 학교 tenant와 사용자 식별 |
| 대회·참가 | CONTEST, REVIEW_ROUND, TEAM, TEAM_MEMBER | 대회 구조와 확정 참가자 명단 |
| 작품·심사 | SUBMISSION, SUBMISSION_FILE, REVIEW_ROUND_ENTRY, REVIEW 계열 | 최종 작품, 원점수, 공식 라운드 판정 |
| 수상 | AWARD | 공식 ENTRY를 근거로 한 팀 단위 상장 |
| Credential | ANC_CREDENTIAL, SOURCE, SUBJECT, STATUS_EVENT | 발급 시점 불변 원문과 주체·상태 snapshot |
| Merkle·Chain | ANC_BATCH, BATCH_ITEM, CHAIN_TRANSACTION, OUTBOX_EVENT | 배치, proof, Kaia 전송, 장애 복구 |

### 반드시 DB 제약으로 막을 것

- USER: 학교 안에서 학번 유일
- TEAM_MEMBER: 팀과 사용자 조합 유일
- SUBMISSION: 팀당 한 건
- REVIEW_ROUND_ENTRY: 라운드와 제출물 조합 유일
- REVIEW_ASSIGNMENT: 심사위원과 ENTRY 조합 유일
- REVIEW: 배정당 한 건
- REVIEW_SCORE_ITEM: 심사와 기준 조합 유일
- AWARD: 공식 ENTRY당 한 건
- ANC_CREDENTIAL_SOURCE: sourceFingerprint 유일
- ANC_BATCH_ITEM: Credential은 sealed batch 하나에만 포함
- 다형성 FK는 타입에 맞는 대상 하나만 non-null

## 5. 업무 원장 결정

### 팀과 상장

```mermaid
flowchart TD
    team["TEAM"] --> rosterLock["팀원 명단 확정"]
    rosterLock --> leader["LEADER"]
    rosterLock --> memberA["MEMBER A"]
    rosterLock --> memberB["MEMBER B"]
    team --> award["팀 단위 AWARD"]
    award --> certificateNo["공유 certificateNo"]
    award --> credential["수상 Credential 한 건"]
    credential --> teamSubject["TEAM subject snapshot"]
    credential --> leaderSubject["LEADER subject snapshot"]
    credential --> memberASubject["MEMBER A subject snapshot"]
    credential --> memberBSubject["MEMBER B subject snapshot"]
    leader --> leaderSubject
    memberA --> memberASubject
    memberB --> memberBSubject
    leaderSubject --> leaderHistory["개인 수상 이력 조회"]
    memberASubject --> memberAHistory["개인 수상 이력 조회"]
    memberBSubject --> memberBHistory["개인 수상 이력 조회"]
```

- **TEAM_MEMBER가 현재 구성원 원장**이다.
- participationFinalizedAt 이후 팀원 추가·삭제·역할 변경을 거부한다.
- 상장은 팀 단위 AWARD와 Credential 한 건으로 발급한다.
- 발급 당시 구성원 전원을 ANC_CREDENTIAL_SUBJECT에 snapshot으로 고정한다.
- 학생별 이력 조회는 현재 팀이 아니라 Credential subject snapshot을 사용한다.

### 제출물 덮어쓰기

```mermaid
flowchart TD
    request(["제출 요청"]) --> open{"제출 기간이 열려 있는가?"}
    open -->|"아니오"| reject["제출 거부"]
    open -->|"예"| lock["TEAM과 기존 SUBMISSION row 조회 (FOR UPDATE)"]
    lock --> finalized{"심사 시작으로 제출이 확정됐는가?"}
    finalized -->|"예"| reject
    finalized -->|"아니오"| upload["새 파일 저장과 SHA-256 계산"]
    upload --> replace["현재 제목과 DB 파일 목록 교체"]
    replace --> commit["DB transaction commit"]
    commit --> cleanupOld["이전 객체 비동기 정리"]
    upload -. "저장 또는 DB 실패" .-> cleanupNew["새 업로드 객체 정리"]
    replace -. "DB rollback" .-> cleanupNew
```

- 재제출 이력 테이블은 현재 만들지 않는다.
- 같은 `SUBMISSION` 행의 제목과 파일 목록을 덮어쓴다.
- TEAM row lock은 최초 제출과 덮어쓰기를 팀 단위로 직렬화하며, 기존 SUBMISSION도 같은 순서로 잠근다.
- SHA-256은 업로드 스트림에서 계산하며 별도 hash worker나 `STALE/READY` 상태를 두지 않는다.
- DB 커밋 뒤 기존 객체를 삭제하고, 롤백되면 새 객체를 삭제한다.
- 제출 마감 또는 첫 심사 시작으로 확정된 뒤에는 수정할 수 없다.

### 라운드 심사와 공식 판정

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 학교 관리자
    actor Judge as 심사위원
    participant API as Contest API
    participant DB as MySQL

    Admin->>API: 라운드 시작
    API->>DB: 대상 제출물 조회
    API->>DB: REVIEW_ROUND_ENTRY 생성
    API->>DB: REVIEW_ASSIGNMENT 생성
    Judge->>API: 기준별 점수와 의견 제출
    API->>DB: REVIEW와 SCORE_ITEM 저장
    API->>DB: 제출된 REVIEW 잠금
    Admin->>API: 라운드 마감 요청
    API->>DB: 제출 심사 집계
    API->>API: 통과 후보와 순위 계산
    Admin->>API: 공식 결과 확정
    API->>DB: finalScore, rankNo, status, finalizedAt 저장
    API->>DB: 라운드 FINALIZED 및 변경 잠금
```

- REVIEW와 REVIEW_SCORE_ITEM은 심사위원별 원점수다.
- REVIEW_ROUND_ENTRY는 학교가 확정한 공식 점수·순위·통과·탈락 원장이다.
- FINALIZED 라운드의 ENTRY, 평가 기준, 배정, 제출된 REVIEW는 수정·삭제할 수 없다.
- AWARD.teamId는 조회용 비정규화 FK이며 ENTRY에서 도달한 TEAM과 같아야 한다.

## 6. Credential 발급 단위

| 유형 | 업무 원천 | 주체 |
| --- | --- | --- |
| PARTICIPATION | 확정 TEAM | 팀과 확정 팀원 |
| WORK | 확정 SUBMISSION | 팀과 작품 기여 구성원 |
| AWARD | CONFIRMED AWARD | 수상 팀과 발급 당시 구성원 |

Credential 발급은 source, subjects, canonical bytes를 한 DB transaction으로 저장한다. 일부만 저장된 DRAFT 상태를 만들지 않고 완성된 READY Credential로 생성한다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 학교 관리자
    participant Domain as 업무 API
    participant DB as MySQL
    participant Issue as Credential Issuance Service
    participant Canon as Canonicalizer

    Admin->>Domain: 참여·작품·수상 확정
    Domain->>DB: 확정 원천과 팀원 조회
    Domain->>Issue: 발급 명령
    Issue->>Issue: sourceFingerprint 계산
    Issue->>DB: 기존 Credential 조회
    alt 기존 Credential 존재
        DB-->>Issue: 기존 결과 반환
    else 신규 Credential
        Issue->>Issue: publicId와 issuedAt 생성
        Issue->>Canon: payload와 file manifest 정규화
        Canon-->>Issue: canonical bytes와 hash
        Issue->>DB: Credential, source, subjects 원자적 저장
    end
    Domain->>DB: 업무 확정과 Credential 함께 commit
```

### 중복 발급 방지

```
subjectSetHash = SHA-256(
  JCS([{subjectPublicId, roleCode, snapshot}, ...]
      sorted by subjectPublicId then roleCode)
)

sourceSnapshotHash = SHA-256(JCS(sourceSnapshot))

sourceFingerprint = SHA-256(
  JCS({
    fingerprintProfileId,
    issuerPublicId,
    credentialType,
    sourceType,
    sourcePublicId,
    sourceSnapshotHash,
    subjectSetHash,
    schemaProfileId
  })
)
```

같은 확정 원천 snapshot과 subject snapshot으로 재시도하면 기존 Credential을 반환한다. 사실을 정정해야 하면 기존 Credential을 수정하지 않고, 정정된 snapshot으로 새 Credential을 발급한 뒤 기존 Credential을 `SUPERSEDED` 처리한다.

## 7. Canonical JSON과 Merkle V1

```mermaid
flowchart TD
    source["확정 업무 원천"] --> sourceSnapshot["Source Snapshot"]
    roster["확정 팀원 명단"] --> subjectSnapshot["Subject Snapshot"]
    sourceSnapshot --> payload["Credential Payload"]
    subjectSnapshot --> payload
    payload --> nfc["Unicode NFC"]
    nfc --> jcs["RFC 8785 JCS"]
    jcs --> canonicalBytes["Canonical Bytes"]
    canonicalBytes --> contentHash["SHA-256 Content Hash"]

    uploadedFiles["업로드 파일 Stream"] --> fileHash["서버 SHA-256"]
    fileHash --> manifest["정렬된 File Manifest"]
    manifest --> manifestCanonical["Manifest Canonical Bytes"]
    manifestCanonical --> fileManifestHash["SHA-256 Manifest Hash"]

    publicId["Credential Public ID"] --> credentialIdHash["Credential ID Hash"]
    schema["Schema Profile ID"] --> schemaVersionHash["Schema Version Hash"]

    contentHash --> leafTuple["ABI-encoded Leaf Tuple"]
    fileManifestHash --> leafTuple
    credentialIdHash --> leafTuple
    schemaVersionHash --> leafTuple
    leafTuple --> leafHash["Double Keccak-256 Leaf"]
    leafHash --> sort["Leaf Hash 정렬"]
    sort --> tree["Sorted-pair Merkle Tree"]
    tree --> root["Merkle Root"]
    tree --> proof["Credential별 Proof"]
```

### 고정 규칙

- 문자열은 Unicode NFC 후 RFC 8785 JCS로 canonicalize한다.
- contentHash와 fileManifestHash는 SHA-256을 사용한다.
- 파일 manifest에는 storageKey, bucket, presigned URL을 넣지 않는다.
- leaf는 issuer, Credential ID, schema, content, manifest hash를 ABI encode한다.
- OpenZeppelin StandardMerkleTree 호환 double Keccak-256 leaf와 sorted-pair node를 사용한다.
- 같은 issuer, schemaVersionHash, treeVersion만 한 batch로 묶는다.

```
leafValue = abi.encode(
  LEAF_DOMAIN,
  issuerId,
  credentialIdHash,
  schemaVersionHash,
  contentHash,
  fileManifestHash
)

leafHash = keccak256(bytes.concat(keccak256(leafValue)))
nodeHash = keccak256(sorted(left, right))
```

## 8. 학교 승인과 Kaia 앵커링

학교 issuer와 가스비를 내는 relayer를 분리한다.

1. 학교 Issuer Signer가 EIP-712 BatchApproval 또는 StatusApproval에 서명한다.
2. Trekkey relayer가 자기 nonce와 KAIA로 표준 EVM transaction을 보낸다.
3. 컨트랙트가 학교 signer, key version, nonce, deadline을 검증한다.
4. 학생과 검증자는 지갑이나 KAIA를 보유하지 않는다.

```mermaid
sequenceDiagram
    autonumber
    participant Batch as Merkle Batch Worker
    participant DB as MySQL
    participant Signer as Issuer Signer
    participant Anchor as Anchor Worker
    participant Relayer as EVM Relayer
    participant Kaia as Kaia Registry

    Batch->>DB: READY Credential claim
    Batch->>Batch: leaf, root, proof 계산
    Batch->>DB: SEALED batch, items, outbox 저장
    Batch->>Signer: EIP-712 BatchApproval 서명 요청
    Signer-->>Batch: keyVersion, nonce, signature
    Batch->>DB: SIGNED batch 저장
    Anchor->>DB: anchor outbox claim
    Anchor->>Anchor: chainId, contract, gas, selector 검증
    Anchor->>Relayer: anchorBatch transaction 생성
    Relayer->>Kaia: transaction 전송과 가스비 지불
    Kaia-->>Relayer: receipt와 BatchAnchored event
    Relayer-->>Anchor: transaction evidence
    Anchor->>Kaia: getBatch readback
    Kaia-->>Anchor: 저장된 root와 issuer key version
    Anchor->>DB: transaction CONFIRMED
    Anchor->>DB: batch와 Credential ANCHORED
```

### Kaia 적용

- Kairos testnet chain ID: 1001
- Mainnet chain ID: 8217
- V1은 Kaia native fee delegation이 아니라 표준 EVM relayer를 사용한다.
- 백엔드 core에는 Kaia SDK 타입을 노출하지 않고 BlockchainAnchorPort로 격리한다.
- 다른 EVM 체인으로 이동할 때 chainId, contractAddress, contractVersion으로 구분한다.

## 9. 공개 검증

```mermaid
sequenceDiagram
    autonumber
    actor Verifier as 검증자
    participant Page as 공개 검증 페이지
    participant API as Verification API
    participant Store as SQL·Object Storage
    participant Kaia as Kaia Registry

    Verifier->>Page: QR URL 또는 package 열기
    Page->>API: 무작위 Credential publicId
    API->>Store: canonical bytes, manifest, proof 조회
    Store-->>API: off-chain 검증 자료
    API->>API: contentHash와 fileManifestHash 재계산
    API->>API: leafHash와 Merkle root 재계산
    API->>Kaia: batch, issuer key, status 조회
    Kaia-->>API: on-chain root와 상태
    API->>API: off-chain root와 on-chain root 비교
    API-->>Page: VALID 또는 구체적 실패 상태
```

### 검증 결과

| 상태 | 의미 |
| --- | --- |
| VALID | hash, proof, issuer, 온체인 상태가 모두 정상 |
| PENDING | 아직 batch anchor가 확정되지 않음 |
| REVOKED | 학교가 Credential을 폐기함 |
| SUPERSEDED | 새 Credential로 대체됨 |
| TAMPERED | canonical hash 또는 Merkle proof 불일치 |
| ANCHOR_NOT_FOUND | 온체인 batch를 찾을 수 없음 |
| ISSUER_INVALID | 발급 시점 issuer key 검증 실패 |
| RPC_UNAVAILABLE | 체인 조회 장애이며 위조로 단정하지 않음 |
| SCHEMA_UNSUPPORTED | 검증기가 schema 또는 tree version을 모름 |

```mermaid
flowchart TD
    start(["검증 시작"]) --> schema{"지원하는 schema와 treeVersion인가?"}
    schema -->|"아니오"| unsupported(["SCHEMA_UNSUPPORTED"])
    schema -->|"예"| hashes{"canonical hash가 일치하는가?"}
    hashes -->|"아니오"| tampered(["TAMPERED"])
    hashes -->|"예"| proof{"Merkle proof가 유효한가?"}
    proof -->|"아니오"| tampered
    proof -->|"예"| rpc{"Kaia 조회가 가능한가?"}
    rpc -->|"아니오"| unavailable(["RPC_UNAVAILABLE"])
    rpc -->|"예"| anchor{"Batch anchor가 존재하는가?"}
    anchor -->|"아니오"| anchorMissing(["ANCHOR_NOT_FOUND"])
    anchor -->|"예"| issuer{"발급 시점 issuer key가 유효한가?"}
    issuer -->|"아니오"| issuerInvalid(["ISSUER_INVALID"])
    issuer -->|"예"| credentialStatus{"Credential 상태는?"}
    credentialStatus -->|"REVOKED"| revoked(["REVOKED"])
    credentialStatus -->|"SUPERSEDED"| superseded(["SUPERSEDED"])
    credentialStatus -->|"정상"| valid(["VALID"])
```

QR에는 학번, 개인정보, 전체 proof를 넣지 않는다. HTTPS 검증 URL과 무작위 Credential publicId만 넣는다.

## 10. 상태 변경

### Credential 상태

```mermaid
stateDiagram-v2
    [*] --> READY: 원천·snapshot·hash 저장
    READY --> BATCHED: sealed batch에 포함
    BATCHED --> ANCHORED: receipt·event·readback 확인
    ANCHORED --> REVOKED: 폐기 승인 온체인 확정
    ANCHORED --> SUPERSEDED: 대체 승인 온체인 확정
    REVOKED --> [*]
    SUPERSEDED --> [*]
```

READY 이후 payload, canonical bytes, source, subjects는 수정하지 않는다. 잘못된 내용은 기존 row를 덮어쓰지 않고 새 Credential 발급과 상태 변경으로 처리한다.

### 폐기와 대체

```mermaid
flowchart TD
    changeRequest(["정정 또는 폐기 요청"]) --> action{"처리 유형"}

    action -->|"폐기"| revokeEvent["DB status event와 outbox"]
    revokeEvent --> revokeSign["StatusApproval 서명"]
    revokeSign --> revokeChain["revokeCredential 온체인 확정"]
    revokeChain --> revokedState(["기존 Credential REVOKED"])

    action -->|"대체"| correctedSource["정정된 원천 snapshot 확정"]
    correctedSource --> newCredential["새 sourceFingerprint와 Credential 생성"]
    newCredential --> newAnchor["새 batch ANCHORED"]
    newAnchor --> supersedeEvent["기존 Credential status event"]
    supersedeEvent --> supersedeSign["StatusApproval 서명"]
    supersedeSign --> supersedeChain["supersedeCredential 온체인 확정"]
    supersedeChain --> oldState(["기존 Credential SUPERSEDED"])
    supersedeChain --> replacement["replacementCredentialIdHash 연결"]
```

- V1은 개별 REVOKED와 SUPERSEDED만 지원한다.
- 대체 발급은 새 Credential이 먼저 ANCHORED된 다음 기존 Credential을 SUPERSEDED 처리한다.
- replacementCredentialIdHash는 0일 수 없다.
- 이미 종료 상태인 Credential에 다른 종료 상태를 덮어쓰지 않는다.

## 11. Outbox와 장애 복구

```mermaid
flowchart LR
    domainTx["업무 상태 변경"] --> sameTx["같은 DB transaction"]
    sameTx --> aggregate[("Aggregate 저장")]
    sameTx --> outbox[("Outbox PENDING 저장")]
    outbox --> claim["Worker claim과 lease"]
    claim --> process{"처리 결과"}
    process -->|"성공"| processed["PROCESSED"]
    process -->|"일시 오류"| retry["attemptCount 증가와 availableAt 갱신"]
    retry --> claim
    process -->|"RPC 결과 불명"| unknown["Chain transaction UNKNOWN"]
    unknown --> readback["온체인 readback"]
    readback -->|"이미 성공"| processed
    readback -->|"미반영"| retry
    process -->|"영구 오류"| dead["DEAD 또는 FAILED"]
    dead --> operator["운영자 조사와 명시적 재처리"]
```

- 업무 확정과 domain outbox 저장은 한 transaction이다.
- Credential과 source·subjects 저장도 한 transaction이다.
- batch seal과 anchor outbox 저장도 한 transaction이다.
- RPC timeout은 FAILED가 아니라 UNKNOWN이다.
- UNKNOWN은 재전송 전에 batch ID 또는 Credential status를 온체인 readback한다.
- relayer nonce는 단일 sequencer 또는 DB lease로 관리한다.
- tx 성공 후 DB 반영이 실패해도 readback으로 복구한다.

### Chain transaction 상태

```mermaid
stateDiagram-v2
    [*] --> PENDING: chain operation 생성
    PENDING --> SUBMITTED: txHash 확보
    SUBMITTED --> CONFIRMED: receipt·event·readback 확인
    SUBMITTED --> UNKNOWN: RPC timeout 또는 응답 불명
    UNKNOWN --> CONFIRMED: 온체인 readback에서 성공 확인
    UNKNOWN --> PENDING: 미반영 확인 후 재전송
    PENDING --> FAILED: 재시도 불가 오류
    SUBMITTED --> FAILED: 명시적 revert
    CONFIRMED --> [*]
    FAILED --> [*]
```

## 12. 보안과 장기 보존

- Issuer·relayer private key는 소스, DB, 일반 환경변수에 저장하지 않는다.
- 학교별 KMS/HSM namespace와 접근 정책을 분리한다.
- 운영 admin은 개인 EOA가 아니라 multisig와 timelock을 사용한다.
- contract allowlist, function selector, value zero, gas cap, 전송 전 eth_call을 검사한다.
- 운영 RPC는 두 provider 이상을 사용하고 receipt, event, block hash를 교차 확인할 수 있게 한다.
- SQL 백업 복구 훈련과 객체 저장소 versioning을 운영한다.
- 학생이 소유할 Portable Credential Package를 제공한다.

### Portable Credential Package

```
credential.json
file-manifest.json
merkle-proof.json
anchor.json
issuer-approval.json
status.json
rendered-certificate.pdf
```

PDF는 사람이 읽는 표시물이고 cryptographic source of truth가 아니다. canonical bytes, proof, chain ID, contract address, schema profile을 함께 보존해야 한다.

## 13. API 경계

```
GET /api/v1/credentials/{publicId}
GET /api/v1/credentials/{publicId}/verify
GET /api/v1/credentials/{publicId}/package

GET /api/v1/organizations/{organizationId}/students/{studentId}/credentials
GET /api/v1/contests/{contestPublicId}/credentials
GET /api/v1/teams/{teamPublicId}/credentials

POST /internal/v1/credentials/issue
POST /internal/v1/batches/{batchPublicId}/seal
POST /internal/v1/batches/{batchPublicId}/submit
POST /internal/v1/credentials/{publicId}/revoke
POST /internal/v1/credentials/{publicId}/supersede
```

공개 검증 API와 관리자 검색 API를 분리한다. 학번 기반 전체 이력 조회는 본인 또는 학교 관리자 인증과 organization scope가 필요하다.

## 14. 중앙형 MVP와 학교별 확장

```mermaid
flowchart TD
    subgraph mvp ["MVP 중앙형 Multi-tenant"]
        centralApi["Trekkey Spring Boot"]
        centralDb[("공용 MySQL")]
        tenantScope["organizationId Scope"]
        signerNamespace["학교별 Signer Namespace"]
        centralApi --> centralDb
        centralApi --> tenantScope
        centralApi --> signerNamespace
    end

    sourcePort["CredentialSourcePort"]
    signerPort["IssuerSignerPort"]
    chainPort["BlockchainAnchorPort"]
    centralApi --> sourcePort
    centralApi --> signerPort
    centralApi --> chainPort

    schoolApiA["학교 A Source Adapter"] --> schoolDbA[("학교 A SQL")]
    schoolApiB["학교 B Source Adapter"] --> schoolDbB[("학교 B SQL")]
    schoolSignerA["학교 A Signer"]
    schoolSignerB["학교 B Signer"]
    sourcePort -.-> schoolApiA
    sourcePort -.-> schoolApiB
    signerPort -.-> schoolSignerA
    signerPort -.-> schoolSignerB

    evmAdapter["표준 EVM JSON-RPC Adapter"] --> registry["Kaia Registry"]
    chainPort --> evmAdapter
```

MVP는 하나의 Spring Boot와 MySQL을 organizationId로 격리한다. 이후 학교가 자기 SQL과 signer를 운영해도 Credential schema, Merkle 규칙, 컨트랙트는 유지하고 Source·Signer Adapter만 교체한다.

## 15. 구현 순서

```mermaid
flowchart LR
    phase1["Phase 1 업무 원장"] --> phase2["Phase 2 체인 독립 Credential"]
    phase2 --> phase3["Phase 3 Merkle와 Solidity"]
    phase3 --> phase4["Phase 4 Spring Boot와 Kaia"]
    phase4 --> phase5["Phase 5 운영 확장"]

    phase1 --> p1a["팀·제출·라운드 잠금"]
    phase1 --> p1b["파일 SHA-256과 Domain Outbox"]
    phase2 --> p2a["Schema Profile과 Canonicalizer"]
    phase2 --> p2b["Source Fingerprint와 Subject Snapshot"]
    phase3 --> p3a["OpenZeppelin 호환 Fixture"]
    phase3 --> p3b["RegistryV1과 EIP-712"]
    phase4 --> p4a["Batch·Anchor Worker"]
    phase4 --> p4b["Kairos 배포와 공개 Verify API"]
    phase5 --> p5a["Mainnet Multisig·KMS·복수 RPC"]
    phase5 --> p5b["학교별 Adapter와 졸업 Credential"]
```

### 완료 기준

- Java canonicalizer와 OpenZeppelin fixture의 leaf, root, proof가 byte-for-byte 일치
- 같은 sourceFingerprint 재처리가 중복 Credential을 만들지 않음
- 동시 재제출에서 오래된 worker가 최신 hash를 덮어쓰지 못함
- 라운드 확정 후 심사와 공식 결과 수정이 실패
- 만료·재사용된 EIP-712 approval이 실패
- 같은 batch ID를 두 번 anchor할 수 없음
- RPC timeout 후 readback으로 성공 여부 복구
- REVOKED와 SUPERSEDED가 VALID로 표시되지 않음
- SQL 백업과 Portable Package로 proof 재검증 가능

## 16. 최종 합의

```mermaid
flowchart LR
    finalized["학교가 업무 사실 확정"] --> immutable["원천·팀원·작품 Snapshot"]
    immutable --> credential["불변 Credential"]
    credential --> batch["Merkle Batch"]
    batch --> kaia["Kaia Root Anchor"]
    credential --> package["학생 소유 Portable Package"]
    kaia --> verify["외부 독립 검증"]
    package --> verify
    verify --> lifetime(["장기 증빙"])

    privacy["개인정보와 원문"] --> offchain[("Off-chain")]
    rootOnly["Root와 상태"] --> onchain[("On-chain")]
```

<aside>

**포트폴리오 설명**

Trekkey는 개인정보와 인증서 원문을 퍼블릭 체인에 저장하지 않는다. 발급 시점의 Credential을 canonical JSON으로 고정하고, 여러 hash를 OpenZeppelin 호환 Merkle Tree로 묶어 root만 Kaia에 앵커링한다. 학교는 EIP-712 approval에 서명하고 Trekkey relayer가 수수료를 부담한다. Transactional Outbox와 멱등 키로 DB와 체인 사이 장애를 복구하며, revoke·supersede와 Portable Package로 정정 및 장기 검증을 지원한다.

</aside>

## 17. 문서와 참고

### GitHub 구현 기준

- 통합 ERD
- Mermaid 다이어그램 보드
- 상세 앵커링 설계
- PR 비교 화면

### 이전 의사결정 이력

- ‣
- ‣

### 기술 기준

- Kaia Foundation Setup
- Kaia execution model
- Kaia fee delegation
- RFC 8785 JSON Canonicalization Scheme
- EIP-712 typed structured data
- OpenZeppelin cryptography
- OpenZeppelin Standard Merkle Tree
