# Trekkey Kairos Registry 배포 및 E2E 검증 기록

- 수행일: 2026-07-28
- 네트워크: Kaia Kairos Testnet
- Chain ID: `1001` (`0x3e9`)
- 컨트랙트: `TrekkeyCredentialRegistryV1`
- Registry: `0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117`
- 상태: Registry 배포, issuer v2·backend relayer 설정, `READ_ONLY`, anchor, QR 대상 API, revoke·supersede E2E 완료
- 미완료: Kaiascan source verification과 mainnet 운영 준비

이 문서는 실제 Kairos 공개 좌표와 트랜잭션을 증적으로 남기고, 같은 검증을 재현하는
절차를 설명한다. Credential 발급 규칙과 운영 구조는
[블록체인 구현 및 실행 가이드](./blockchain-implementation-runbook.md)를 기준으로 한다.

## 1. 현재 공개 좌표

### 네트워크와 Registry

| 항목 | 값 |
| --- | --- |
| Network | `Kaia Kairos Testnet` |
| Chain ID | `1001` |
| RPC | `https://public-en-kairos.node.kaia.io` |
| Explorer | `https://kairos.kaiascan.io` |
| Contract | `TrekkeyCredentialRegistryV1` |
| Contract version | `1` |
| Solidity | `0.8.28`, optimizer `200`, EVM target `london` |
| Registry | [`0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117`](https://kairos.kaiascan.io/address/0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117) |
| Runtime code | `10,522 bytes` |
| Runtime code hash | `0x6bdcd078a99c833e1e6126d71954b570fb7bfb4afe4720fc4a438009039571a2` |

Kaia Wallet은 테스트 토큰도 `KAIA`로 표시한다. Mainnet과 Kairos는 토큰 기호가 아니라
chain ID로 구분한다.

- Kairos: `1001`
- Kaia Mainnet: `8217`

이번 배포와 E2E는 전부 `1001`에서 수행했다.

### 현재 사용 역할

| 역할 | 주소 | 용도 |
| --- | --- | --- |
| Deployer / 초기 관리자 | `0x5cc66C91d390336bD58Db75C261a994A78C14486` | 배포와 초기 역할 설정 |
| Backend relayer | `0xB87670C4171e913368F688B660e143366E0ca6ea` | raw transaction 서명, 전송, 테스트 KAIA 납부 |
| School issuer signer v2 | `0x235a99Eb7Acb6f181740B246acfc9885692BD79d` | EIP-712 batch·상태 승인, KAIA 불필요 |

issuer와 relayer를 분리한다. issuer는 학교의 승인 권한이고 relayer는 승인된 요청을
전송하는 권한이다. 두 역할을 같은 키로 합치면 하나의 키 탈취로 승인과 전송 권한이 함께
노출된다.

### 학교 issuer 식별자

| 항목 | 값 |
| --- | --- |
| `ORGANIZATION.publicId` | `725050e0-2a2f-48a8-a8b8-2e51e12524b7` |
| Domain-separated `issuerId` | `0x21240e02415b5fef8fdada2d39b44494fe28d292dab8ecb1f62645fd44298184` |
| Active test key version | `2` |
| Registered signer | `0x235a99Eb7Acb6f181740B246acfc9885692BD79d` |
| `validUntil` | `0`, 활성 |
| `compromisedAt` | `0`, 침해 기록 없음 |

issuer ID 계산은 표시 이름이나 내부 bigint PK가 아니라 변경되지 않는 UUID를 사용한다.

```text
issuerDomain = keccak256(utf8("TREKKEY_ISSUER_ID_V1"))
publicIdHash = keccak256(utf8(ORGANIZATION.publicId))
issuerId = keccak256(abi.encode(issuerDomain, publicIdHash))
```

`KairosReadOnlyLiveTest`와 `KairosCredentialLiveE2ETest`는 격리된 H2에 실제 Spring/JPA
`Organization` 엔터티를 저장한 뒤 production issuer 동기화 서비스를 호출했다. 따라서
Java 엔터티, UUID, issuer ID, 온체인 key v2 연결은 검증됐다.

다만 팀 공용 MySQL의 기존 `ORGANIZATION` 행 backfill과 운영
`NOT NULL + UNIQUE` migration까지 완료했다는 뜻은 아니다. 이 작업은 mainnet 전에 별도로
수행한다.

### 이전 Kairos 역할

| 항목 | 값 | 현재 처리 |
| --- | --- | --- |
| Issuer key v1 | `0x16912745426d9A108A91e303dd865B1045B75aE1` | 과거 시험 증적 보존, 신규 E2E에는 미사용 |
| Browser relayer | `0xFf3BFF4FfF5d0699E82Fd5b045F034AB01FF8Ed6` | 역할은 남아 있으나 backend E2E에는 미사용 |

v1과 browser relayer를 문서에서 지우지 않는 이유는 이미 발생한 온체인 기록을 추적하기
위해서다. 운영 전에는 관리자 권한 이전, 미사용 relayer role 회수, issuer key retirement를
별도의 change 절차로 수행한다.

## 2. 키 보관 결정

### Kairos 로컬 E2E

테스트 전용 issuer와 relayer 키는 macOS Keychain에서 서로 다른 service로 보관한다.

```text
io.trekkey.kairos-e2e.issuer
io.trekkey.kairos-e2e.relayer
```

생성 및 공개 주소 확인:

```bash
cd contracts
./scripts/kairos-e2e-keychain.sh bootstrap
./scripts/kairos-e2e-keychain.sh addresses
```

helper는 private key를 반환하는 공개 하위 명령을 제공하지 않는다. opt-in E2E 명령이
Keychain 값을 no-daemon JVM 환경에만 전달하고 프로세스 종료와 함께 폐기한다. shell tracing,
core dump와 느슨한 임시 파일 권한도 차단한다.

### 운영

운영 방식은 AWS KMS 비반출 키로 고정한다.

| 설정 | 값 |
| --- | --- |
| Key spec | `ECC_SECG_P256K1` |
| Key usage | `SIGN_VERIFY` |
| Signing algorithm | `ECDSA_SHA_256` |
| Sign input | EIP-712 또는 EVM transaction의 Keccak-256 digest |
| KMS message type | `DIGEST` |

`MessageType=DIGEST`가 중요하다. EIP-712와 EVM transaction은 이미 Keccak-256 digest를
만들기 때문에 KMS가 이를 다시 SHA-256으로 해시하면 컨트랙트가 복구하는 주소와 서명이
일치하지 않는다.

- issuer와 relayer는 서로 다른 KMS key와 IAM policy를 사용한다.
- worker IAM에는 대상 키의 `kms:GetPublicKey`, `kms:Sign`만 허용한다.
- DB `signerRef`에는 KMS ARN 또는 alias만 저장한다.
- Git, Notion, SQL, 로그, Secrets Manager에 평문 private key를 저장하지 않는다.
- KMS의 DER ECDSA 서명을 `(r,s)`로 변환하고 low-s 정규화와 recovery ID 계산을 수행한다.
- relayer public key에서 EVM 주소를 계산하고 Kaia `AccountKeyLegacy` EOA로 사용한다.

현재 저장소의 `LOCAL_RELAYER`는 Kairos E2E 전용이다. AWS KMS adapter와 장애 전환 시험
완료 전에는 mainnet write mode를 열지 않는다.

## 3. 온체인 설정 증적

### Registry 최초 배포

| 목적 | Tx hash | Block | Gas used | 수수료 |
| --- | --- | ---: | ---: | ---: |
| Registry 배포 | [`0xf3b88b69...b11eb34`](https://kairos.kaiascan.io/tx/0xf3b88b691c8cf475576c2178e2f7325f105dce1e0694dbe121c2140f0b11eb34) | `223574298` | `2,444,794` | `0.067231835 KAIA` |
| Browser relayer role | [`0xda3e3952...560230`](https://kairos.kaiascan.io/tx/0xda3e39525679ec2029c2bdc705a647298c824d1d53b6a42dfc2da8ecd9560230) | `223574532` | `51,537` | `0.0014172675 KAIA` |
| Issuer key v1 등록 | [`0x35f4844b...41cb10`](https://kairos.kaiascan.io/tx/0x35f4844b8932ac2b5a3d9361b0bb1ee7f2ac64063e5b425f12e2c40e8241cb10) | `223574553` | `52,412` | `0.00144133 KAIA` |

세 receipt는 모두 `status=1`이다.

### Backend E2E 역할 설정

| 목적 | Tx hash | Block | Gas used | 수수료 |
| --- | --- | ---: | ---: | ---: |
| Backend relayer role | [`0xd8ad1100...fc40cb`](https://kairos.kaiascan.io/tx/0xd8ad1100c951337e56f029461c891765fca1fb0c776e797e9f508ecbccfc40cb) | `223605014` | `51,537` | `0.0014172675 KAIA` |
| Backend relayer 1 KAIA 충전 | [`0x69244c44...964a9`](https://kairos.kaiascan.io/tx/0x69244c4456deee9c4b37e85da408382ba5015f2208115a7c1e2fa0d476a964a9) | `223605097` | `21,000` | `0.0005775 KAIA` |
| Issuer key v2 등록 | [`0xe4a093e3...11d574`](https://kairos.kaiascan.io/tx/0xe4a093e31ed1c79b41d6b5c0b0f0668a00a77f7fc2b07da97f2de0642511d574) | `223605390` | `52,412` | `0.00144133 KAIA` |

세 receipt도 모두 `status=1`이다. 최종 readback은 다음을 확인했다.

```text
runtime code: present and matching
deployer admin: true
issuer key admin: true
backend relayer role: true
issuer key version: 2
issuer signer: 0x235a99eb7acb6f181740b246acfc9885692bd79d
validUntil: 0
compromisedAt: 0
```

## 4. Spring READ_ONLY 검증

실행:

```bash
./contracts/scripts/run-kairos-read-only.sh
```

시험은 다음 순서로 진행된다.

```text
실제 Organization 엔터티 저장
-> publicId UUID 조회
-> production issuer ID 계산
-> Registry getIssuerKey(version 2)
-> signer 주소 비교
```

확인된 출력:

```text
KAIROS_READ_ONLY
organizationPublicId=725050e0-2a2f-48a8-a8b8-2e51e12524b7
issuerKeyVersion=2
signer=0x235a99eb7acb6f181740b246acfc9885692bd79d
```

이 시험은 relayer private key 환경변수를 명시적으로 제거한 상태에서 실행된다. 따라서
`READ_ONLY` 연결이 쓰기 secret 없이도 contract와 issuer를 조회한다는 점을 검증한다.

## 5. Credential E2E 증적

실행:

```bash
./contracts/scripts/run-kairos-live-e2e.sh
```

시험은 production 서비스를 사용해 Credential 세 건을 발급하고 하나의 Merkle batch로 묶는다.
그 다음 issuer v2의 EIP-712 승인을 만들고 backend relayer가 anchor, revoke, supersede
트랜잭션을 차례로 보낸다.

### Batch

| 항목 | 값 |
| --- | --- |
| Batch public ID | `8c740025-e0c7-4299-abb0-da19cb11ecae` |
| Batch ID hash | `0x359d7bded0ae41c1f12388aabd0385de5e5ac6c8654c87db7175251827575c44` |
| Merkle root | `0x5a606d5e04245f906f00de8d63cf42edc4ce3dae89758f08ba5b34610dcf053f` |
| Schema version hash | `0xc777f42e5ef4378b63c35a4fba020c26f8c26f23d7069bc924b924c86449da0c` |
| Leaf count | `3` |
| Approval nonce | `2354121488374261994` |
| Issuer key version | `2` |

### 상태 트랜잭션

| 작업 | Tx hash | Block | Nonce | Gas used | 수수료 |
| --- | --- | ---: | ---: | ---: | ---: |
| Anchor | [`0xb2a2bc95...ec322`](https://kairos.kaiascan.io/tx/0xb2a2bc95949b8f8450d2f1a3d010b9626cb51c27aa0a2c1b2cc96d62bebec322) | `223605940` | `1` | `202,689` | `0.0055739475 KAIA` |
| Revoke | [`0x954d86e6...7aeb3`](https://kairos.kaiascan.io/tx/0x954d86e63269c9b74ec3c4a0f84bf6725013621cee43c578ed662cfe8497aeb3) | `223605941` | `2` | `112,904` | `0.00310486 KAIA` |
| Supersede | [`0xee4715ee...5f72d`](https://kairos.kaiascan.io/tx/0xee4715ee4ea971eae2e48b791c9f36f9cc94dad65902c289fd63c45da745f72d) | `223605942` | `3` | `133,525` | `0.0036719375 KAIA` |

세 receipt는 모두 `status=1`이다.

### 공개 QR 대상 API

QR이 가리킬 공개 경로:

```text
GET /api/public/credentials/{credentialPublicId}
```

검증된 Credential:

| 역할 | Public ID | 최종 상태 |
| --- | --- | --- |
| 폐기 대상 | `4287612f-e3ed-4384-8b55-bbdf8581c4ed` | `VALID → REVOKED` |
| 대체 대상 | `70c148f3-0b17-4382-a7bb-16eead191a86` | `VALID → SUPERSEDED` |
| 대체 Credential | `295cec86-4928-4c27-b2ac-b0d2391dd852` | `VALID` |

독립 Registry readback:

| Credential | ID hash | State | Replacement |
| --- | --- | ---: | --- |
| 폐기 | `0xcd189add2b9f2a49dad642bab3f765de639644d57500b59cf709d10abfb32ac9` | `1` | 없음 |
| 대체 | `0x80dc3ec1058856b051480b3b87c89d8a4ae4d854b2b3644b380c61743cf303a8` | `2` | `0x9e81bcca75b0db81794aee84b41de75d586e5cffb7c58afd14df66b63baac9f7` |
| 새 Credential | `0x9e81bcca75b0db81794aee84b41de75d586e5cffb7c58afd14df66b63baac9f7` | `0` | 없음 |

컨트랙트의 상태 숫자는 `0=VALID`, `1=REVOKED`, `2=SUPERSEDED`다. 백엔드는 Merkle proof만
확인하지 않고 이 상태까지 함께 읽어 최종 검증 결과를 만든다.

E2E 종료 후 backend relayer 잔액은 `0.9820749775 KAIA`였다.

## 6. 실체인에서 발견한 RPC 일관성 문제

첫 E2E 시도에서 anchor transaction은 성공했지만, 성공 receipt 직후 같은 public RPC의
`getBatch`가 잠시 이전 상태를 반환했다.

| 항목 | 값 |
| --- | --- |
| Diagnostic anchor tx | [`0xb382fe01...e3c4`](https://kairos.kaiascan.io/tx/0xb382fe01faca23d758c383428bbce6293e6dbe0449c75547f13b8ad31e94e3c4) |
| Block | `223605587` |
| Receipt | `status=1` |
| Diagnostic root | `0xae5102708efc7f1c3570ccb04af32ac95fa743f8461a5f5a3469ad870ac9283c` |
| Batch ID hash | `0x4231bf4fdd0f91b833febe6e6b2267c63abaf820a41f1fc7dd8b18689071291e` |

나중에 독립 readback한 calldata와 `getBatch` 값은 모두 일치했다. 원인은 public RPC
load balancer 뒤 노드 간 일시적인 read-after-write 지연으로 판단했다.

worker 처리 원칙을 다음과 같이 수정했다.

```text
성공 receipt + 즉시 readback 불일치
-> FAILED로 확정하지 않음
-> 같은 tx hash를 UNKNOWN으로 보존
-> 온체인 증거가 보일 때까지 재조회하고 장기 체류 시 운영 경보
-> 일치하면 CONFIRMED
```

이 과정에서 새 nonce나 새 raw transaction을 만들지 않는다. 이미 성공한 transaction을
중복 전송하지 않고 공개 증거가 조회 가능한 시점까지 기다린다. 성공 receipt와 예상 event가
있는 트랜잭션을 readback 지연만으로 자동 `FAILED` 처리하지 않는다.

## 7. 재현 절차

### 1. 의존성 검증

```bash
cd contracts
nvm use
npm ci
npm run fixture:merkle
npm test
npx tsc --noEmit
```

Java:

```bash
cd ..
./gradlew test --no-daemon
```

### 2. 공개 설정

`contracts/.env`에는 현재 공개 좌표만 넣는다.

```dotenv
KAIROS_RPC_URL=https://public-en-kairos.node.kaia.io
DEPLOYER_ADDRESS=0x5cc66C91d390336bD58Db75C261a994A78C14486
REGISTRY_ADDRESS=0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117
ISSUER_PUBLIC_ID=725050e0-2a2f-48a8-a8b8-2e51e12524b7
ISSUER_KEY_VERSION=2
ISSUER_SIGNER_ADDRESS=0x235a99Eb7Acb6f181740B246acfc9885692BD79d
RELAYER_ADDRESS=0xB87670C4171e913368F688B660e143366E0ca6ea
```

private key, seed phrase, Wallet 비밀번호는 이 문서와 Git에 넣지 않는다.

### 3. 온체인 공개 상태 조회

```bash
cd contracts
npm run read:kairos
```

다음을 모두 확인한다.

- Registry runtime code 존재
- Registry 주소·runtime code hash가 공개 manifest와 일치
- deployer admin 역할
- deployer issuer-key admin 역할
- backend relayer 역할
- issuer key v2 signer 일치
- issuer key 활성 상태
- relayer balance

### 4. 백엔드 읽기 검증

```bash
cd ..
./contracts/scripts/run-kairos-read-only.sh
```

### 5. 쓰기 E2E

이 명령은 실제 Kairos 트랜잭션 세 건과 가스비를 발생시킨다. 테스트 목적과 잔액을 확인한
뒤 실행한다.

```bash
KAIROS_LIVE_E2E_CONFIRM=I_UNDERSTAND_KAIROS_WRITES \
  ./contracts/scripts/run-kairos-live-e2e.sh
```

반복 실행할 때마다 새 Credential, batch, nonce와 트랜잭션이 생긴다. 단위 테스트나 일반
CI에서는 실행하지 않는다. wrapper는 확인 문자열과 read-only manifest preflight가 모두
통과된 경우에만 `KAIROS_LIVE_E2E=true` 경로를 연다.

## 8. 장애와 복구 원칙

### Tx hash가 있는 경우

```text
receipt 조회
-> pending이면 기다림
-> status=1이면 event와 contract readback 비교
-> readback 지연이면 UNKNOWN에서 같은 tx hash 재조회
-> 성공 receipt는 readback 지연만으로 FAILED 처리하지 않음
-> 명시적 revert 또는 영구 RPC 오류만 FAILED
```

### Issuer signer가 잘못 등록된 경우

같은 key version을 덮어쓰지 않는다. 새 key version을 등록하고 DB의 issuer key 원장도 새
버전으로 동기화한다. 이미 발급된 Credential은 발급 당시 key version으로 검증한다.

### Registry를 교체하는 경우

V1은 proxy upgrade를 사용하지 않는다. 새 contract version을 새 주소에 배포한다. 기존
Credential 검증을 유지하려면 mainnet 배포 전에 chain/contract registry 또는 router를
구현해야 한다.

## 9. 남은 작업

- [x] Kairos Registry 배포와 runtime readback
- [x] 별도 issuer v2와 backend relayer 생성·역할 등록
- [x] 테스트 키를 macOS Keychain에 분리 보관
- [x] 실제 Spring/JPA `Organization.publicId` 기반 `READ_ONLY` 검증
- [x] Credential 3건, Merkle batch, root anchor
- [x] 공개 QR 대상 API `VALID`, `REVOKED`, `SUPERSEDED` 검증
- [x] 독립 Registry readback
- [x] public RPC read-after-write 지연 복구 로직과 회귀 테스트
- [ ] 공용 MySQL `ORGANIZATION.publicId` backfill 및 `NOT NULL + UNIQUE` migration
- [ ] QR 스캔용 프론트 화면과 브라우저 E2E
- [ ] AWS KMS issuer·relayer adapter와 IAM 정책
- [ ] 운영 multisig/timelock과 미사용 Kairos 역할 정리
- [ ] Outbox, relayer 잔액, 가스 예산 모니터링
- [ ] MySQL, Object Storage, Portable Package 백업·복구 훈련
- [ ] Kaiascan source-code verification
- [ ] Kaia Mainnet 배포

Kaiascan source verification은 Solidity source와 build metadata를 외부 서비스에 공개
업로드한다. 다음과 같은 명시적 승인을 받은 뒤에만 수행한다.

```text
Solidity 소스와 빌드 메타데이터를 Kaiascan에 업로드해도 된다.
```

승인 후 실행 명령:

```bash
cd contracts
npm run verify:kairos -- \
  0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117 \
  0x5cc66C91d390336bD58Db75C261a994A78C14486
```

## 10. 저장소 기준 파일

| 파일 | 역할 |
| --- | --- |
| `contracts/deployments/kairos-1001.json` | 프로그램이 읽을 수 있는 공개 배포와 E2E 좌표 |
| `contracts/.env` | Git에서 제외된 로컬 공개 설정 |
| `contracts/scripts/kairos-e2e-keychain.sh` | Kairos 테스트 키 Keychain helper |
| `contracts/scripts/run-kairos-read-only.sh` | secret 없는 Spring issuer readback |
| `contracts/scripts/run-kairos-live-e2e.sh` | opt-in 실체인 Credential E2E |
| `contracts/scripts/read-kairos-state.mjs` | 독립 Registry·role·balance 조회 |
| `docs/blockchain-implementation-runbook.md` | 전체 구현과 운영 실행 가이드 |
| 이 문서 | 실제 Kairos 배포·E2E 증적과 재현 절차 |
