# Trekkey Kairos Registry 배포 기록과 재현 절차

- 수행일: 2026-07-28
- 네트워크: Kaia Kairos Testnet
- Chain ID: `1001` (`0x3e9`)
- 컨트랙트: `TrekkeyCredentialRegistryV1`
- 배포 방식: Kaia Wallet Chrome 확장 프로그램
- 상태: 배포, relayer 역할 부여, issuer key 등록, 온체인 readback 검증 완료

이 문서는 두 목적을 가진다.

1. 2026-07-28에 실제 Kairos에서 수행한 배포 결과를 공개 증적으로 남긴다.
2. private key를 파일로 내보내지 않고 같은 절차를 재현하는 운영 순서를 제공한다.

일반적인 Credential 발급, Merkle 배치, worker 운영 절차는
[블록체인 구현 및 실행 가이드](./blockchain-implementation-runbook.md)를 기준으로 한다.

## 1. 배포 결과

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

Kaia Wallet은 Kairos 테스트 토큰도 `KAIA`로 표시한다. 네트워크는 토큰 기호가 아니라
`Kairos`, chain ID `1001`, `kairos.kaiascan.io`로 판별한다. Kaia Mainnet chain ID는
`8217`이며 이 배포에는 사용하지 않았다.

### 참여 계정

| 역할 | 주소 | Test KAIA 필요 여부 |
| --- | --- | --- |
| Deployer / 초기 관리자 | `0x5cc66C91d390336bD58Db75C261a994A78C14486` | 필요 |
| Trekkey relayer | `0xFf3BFF4FfF5d0699E82Fd5b045F034AB01FF8Ed6` | 실제 앵커링 전송 시 필요 |
| School issuer signer | `0x16912745426d9A108A91e303dd865B1045B75aE1` | 불필요 |

세 역할을 분리한 이유는 다음과 같다.

- Deployer는 코드를 배포하고 초기 권한만 설정한다.
- Issuer signer는 학교가 Credential 배치를 승인했다는 EIP-712 서명을 만든다.
- Relayer는 서명된 요청을 Kaia에 전송하고 가스비를 낸다.

Issuer와 relayer를 같은 키로 합치면 학교 승인 권한과 서비스 전송 권한이 한 번에 탈취될 수
있으므로 분리한다.

### 학교 issuer

| 항목 | 값 |
| --- | --- |
| Kairos test issuer public ID | `725050e0-2a2f-48a8-a8b8-2e51e12524b7` |
| Domain-separated `issuerId` | `0x21240e02415b5fef8fdada2d39b44494fe28d292dab8ecb1f62645fd44298184` |
| Key version | `1` |
| Registered signer | `0x16912745426d9A108A91e303dd865B1045B75aE1` |
| `validFrom` | `2026-07-28T05:49:16Z` |
| `validUntil` | `0`, 활성 상태 |
| `compromisedAt` | `0`, 침해 기록 없음 |

이번 UUID는 Registry 배포를 위해 먼저 고정한 Kairos 테스트 식별자다. 아직 실제 학교 DB
행과의 매핑은 완료되지 않았다. Credential 통합 시험 전 아래 둘 중 하나를 선택해야 한다.

1. 의도한 테스트 학교의 `ORGANIZATION.publicId`를 이 UUID로 고정한다.
2. DB에서 이미 고정한 다른 UUID가 있다면 그 UUID의 새 `issuerId`를 계산해 별도로 issuer
   key를 등록한다.

온체인에 등록한 `issuerId`와 DB의 학교 UUID가 다르면 백엔드 issuer 동기화와 배치 승인이
실패한다. 표시 이름이나 내부 bigint PK로 맞추지 않는다.

일반적으로 `issuerId`는 다음과 같이 `ORGANIZATION.publicId`에서 domain-separated hash로
계산한다.

```text
issuerDomain = keccak256(utf8("TREKKEY_ISSUER_ID_V1"))
publicIdHash = keccak256(utf8(ORGANIZATION.publicId))
issuerId = keccak256(abi.encode(issuerDomain, publicIdHash))
```

## 2. 온체인 트랜잭션 증적

모든 시각은 블록 timestamp 기준이다.

| 순서 | 목적 | Tx hash | Block | Gas used | 수수료 |
| --- | --- | --- | ---: | ---: | ---: |
| 1 | Registry 배포 | [`0xf3b88b69...b11eb34`](https://kairos.kaiascan.io/tx/0xf3b88b691c8cf475576c2178e2f7325f105dce1e0694dbe121c2140f0b11eb34) | `223574298` | `2,444,794` | `0.067231835 Test KAIA` |
| 2 | Relayer 역할 부여 | [`0xda3e3952...560230`](https://kairos.kaiascan.io/tx/0xda3e39525679ec2029c2bdc705a647298c824d1d53b6a42dfc2da8ecd9560230) | `223574532` | `51,537` | `0.0014172675 Test KAIA` |
| 3 | Issuer key 등록 | [`0x35f4844b...41cb10`](https://kairos.kaiascan.io/tx/0x35f4844b8932ac2b5a3d9361b0bb1ee7f2ac64063e5b425f12e2c40e8241cb10) | `223574553` | `52,412` | `0.00144133 Test KAIA` |

```text
Registry 배포:        2026-07-28T05:45:01Z / 2026-07-28 14:45:01 KST
Relayer 역할 부여:    2026-07-28T05:48:55Z / 2026-07-28 14:48:55 KST
Issuer key 등록:      2026-07-28T05:49:16Z / 2026-07-28 14:49:16 KST
총 사용 수수료:       0.0700904325 Test KAIA
```

세 receipt 모두 `status=1`이며 예상 Registry에서 기대한 event가 발생했다.

설정 트랜잭션의 ABI 디코딩 결과:

```text
grantRole(
  0xe2b7fb3b832174769106daebcfd6d1970523240dda11281102db9363b83b0dc4,
  0xFf3BFF4FfF5d0699E82Fd5b045F034AB01FF8Ed6
)

0xe2b7...0dc4 = keccak256("RELAYER_ROLE")
```

```text
registerIssuerKey(
  0x21240e02415b5fef8fdada2d39b44494fe28d292dab8ecb1f62645fd44298184,
  1,
  0x16912745426d9A108A91e303dd865B1045B75aE1
)
```

Kaia Wallet이 `함수 유형: 찾을 수 없음`이라고 표시할 수 있다. 이는 Wallet에 ABI가 없다는
뜻이며 실패나 위험 판정이 아니다. 이 경우 대상 주소, selector, ABI로 디코딩한 인자를
확인한 뒤 승인한다.

## 3. 사전 준비

### 도구

- Node.js 22 LTS
- Chrome
- Kaia Wallet Chrome 확장 프로그램
- Kairos 테스트 KAIA를 받은 deployer
- 서로 다른 deployer, issuer signer, relayer 주소
- DB에 고정할 테스트 `ORGANIZATION.publicId` UUID

### 공개 설정

`contracts/.env`에는 아래 공개값만 넣는다.

```dotenv
KAIROS_RPC_URL=https://public-en-kairos.node.kaia.io
DEPLOYER_ADDRESS=0x5cc66C91d390336bD58Db75C261a994A78C14486
REGISTRY_ADDRESS=
ISSUER_PUBLIC_ID=725050e0-2a2f-48a8-a8b8-2e51e12524b7
ISSUER_KEY_VERSION=1
ISSUER_SIGNER_ADDRESS=0x16912745426d9A108A91e303dd865B1045B75aE1
RELAYER_ADDRESS=0xFf3BFF4FfF5d0699E82Fd5b045F034AB01FF8Ed6
```

Kaia Wallet을 사용하는 이 절차에는 `DEPLOYER_PRIVATE_KEY`와 `ISSUER_PRIVATE_KEY`가 필요하지
않다. seed phrase와 private key를 Git, Notion, 메신저, 로그에 입력하지 않는다.

### 배포 전 테스트

```bash
cd contracts
nvm use
npm ci
npm run fixture:merkle
npm test
npx tsc --noEmit
```

2026-07-28 배포 직전 확인 결과:

- Solidity 테스트 `12/12` 통과
- Wallet deployer 서버 테스트 `9/9` 통과
- TypeScript 검사 통과
- Spring Boot 테스트 `305`개 통과, 실패·오류 `0`, MySQL 의존 테스트 `4`개 조건부 skip
- 예상 deployment gas `2,444,794`
- Deployer와 relayer는 Kairos `AccountKeyLegacy`

## 4. 단계별 재현 절차

### 1단계: 로컬 배포 콘솔 실행

```bash
cd contracts
npm run deploy:kairos:wallet
```

브라우저에서 `http://127.0.0.1:4173`을 연다.

서버는 loopback에만 바인딩된다. UI, 로컬 ethers bundle, contract artifact, 공개 설정만
allowlist로 제공하며 `.env`, private key, 임의의 `node_modules`, 상위 경로를 제공하지 않는다.

### 2단계: Deployer 연결

Kaia Wallet에서 deployer를 선택하고 화면의 `Kaia Wallet 연결`을 누른다.

화면에서 반드시 확인한다.

```text
네트워크: Kaia Kairos (1001)
연결 역할: deployer
연결 주소: DEPLOYER_ADDRESS
Registry: 미배포
```

화면은 chain ID가 `1001`이 아니거나, 연결 주소가 설정된 deployer 또는 issuer가 아니면
다음 동작을 허용하지 않는다.

### 3단계: Registry 배포

`Registry 배포`를 누른다. Kaia Wallet 승인창에서 다음을 확인한다.

```text
Network: Kairos 테스트넷
Transaction: Contract Deployment
From: deployer
Value: 0 KAIA
Origin: 127.0.0.1
```

승인하면 배포 콘솔은 tx hash와 예상 contract address를 즉시 브라우저 저장소에 고정한다.
새로고침, RPC timeout, 브라우저 종료가 발생해도 저장된 tx의 receipt를 먼저 복구하며 결과가
불명확한 동안 중복 배포를 막는다.

receipt가 성공하면 다음을 확인한다.

1. `contractAddress`가 예상 주소와 같다.
2. 해당 주소에 코드가 존재한다.
3. immutable byte 범위만 제외한 runtime bytecode가 로컬 artifact와 정확히 같다.
4. deployer가 초기 관리자 역할을 가진다.

### 4단계: Issuer signer 소유 증명

Kaia Wallet을 issuer signer 계정으로 바꾸고 배포 화면에서 다시 연결한다. 계정 전환만 하고
다시 연결하지 않으면 이전 signer 객체를 사용하지 않도록 화면이 동작을 차단한다.

`Issuer signer 검증`의 `서명`을 누른다. 이 단계는 트랜잭션이 아닌 EIP-712 메시지 서명이라
가스가 들지 않고 issuer 계정 잔액이 0이어도 된다.

2026-07-28에 확인한 typed data:

```text
Domain
  name: TrekkeyCredentialRegistry
  version: 1
  chainId: 0x3e9
  verifyingContract: 0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117

Message
  issuerId: 0x21240e02415b5fef8fdada2d39b44494fe28d292dab8ecb1f62645fd44298184
  keyVersion: 1
  signer: 0x16912745426d9A108A91e303dd865B1045B75aE1
  statementHash: 0x9985a27e5229b4a75af22dfc27bc0b23c35b64e7f4ac8f07561af499ed17ba92
```

`statementHash`는 다음 고정 문구의 hash다.

```text
keccak256(utf8("TREKKEY_ISSUER_KEY_PROOF_V1"))
```

화면은 서명에서 복구한 EVM 주소가 설정된 issuer signer와 정확히 같은지 확인한다. 이 검증은
오타 주소나 Kaia 역할 기반 키처럼 계정 주소와 ECDSA 복구 주소가 다른 키를 잘못 등록하는
사고를 막는다. 서명 원문은 Git에 저장하지 않는다.

### 5단계: Deployer 복귀 및 권한 설정

Kaia Wallet을 deployer로 되돌리고 배포 화면에서 다시 연결한다. `권한 및 issuer 등록`의
`설정`을 누른다.

화면은 현재 온체인 값을 먼저 읽는다.

1. relayer에 역할이 없으면 `grantRole(RELAYER_ROLE, relayer)`를 요청한다.
2. `issuerId + keyVersion`이 비어 있으면 `registerIssuerKey`를 요청한다.
3. 이미 같은 값이면 트랜잭션 없이 통과한다.
4. 같은 key version에 다른 signer가 있으면 덮어쓰지 않고 실패한다.

설정 과정에서 Wallet 승인창이 두 번 나타날 수 있다.

```text
승인 1: Relayer 역할 부여
승인 2: Issuer signer 등록
```

각 Wallet 창에서 Kairos 테스트넷, deployer, Registry 주소, `0 KAIA` 전송을 확인한다.

### 6단계: 최종 온체인 검증

`온체인 검증`은 읽기 전용이며 가스가 들지 않는다. 다음을 모두 확인한다.

1. 배포 runtime bytecode 일치
2. EIP-712 issuer 소유 증명 유효
3. Deployer의 `DEFAULT_ADMIN_ROLE`
4. Deployer의 `ISSUER_KEY_ADMIN_ROLE`
5. Relayer의 `RELAYER_ROLE`
6. `issuerId + keyVersion=1`의 signer 주소
7. issuer key 활성 상태
8. `LEAF_DOMAIN = keccak256("TREKKEY_CREDENTIAL_LEAF_V1")`

2026-07-28 readback 결과:

```text
initial admin: true
issuer key admin: true
relayer role: true
issuer signer: 0x16912745426d9A108A91e303dd865B1045B75aE1
validUntil: 0
compromisedAt: 0
LEAF_DOMAIN: 0x5d94c81e6ec3080e984cba1adb7df83a737c11ae46c6d9b02eb62d3ed54d58ed
```

## 5. 백엔드에 반영할 값

최초 연결은 전송을 차단한 `READ_ONLY`로 시작한다.

```dotenv
BLOCKCHAIN_ANCHORING_MODE=READ_ONLY
BLOCKCHAIN_CHAIN_ID=1001
BLOCKCHAIN_RPC_URL=https://public-en-kairos.node.kaia.io
BLOCKCHAIN_CONTRACT_ADDRESS=0x4ca738CC22Af5aE40EA8A23E001FA93e1e044117
BLOCKCHAIN_CONTRACT_VERSION=1
BLOCKCHAIN_TREE_VERSION=1
BLOCKCHAIN_WORKER_ENABLED=false
```

읽기 검증과 issuer key 동기화가 끝난 뒤, 실제 트랜잭션을 보내는 worker 인스턴스 하나만
`LOCAL_RELAYER`와 `BLOCKCHAIN_WORKER_ENABLED=true`로 전환한다.

중요한 현재 제약:

- Spring `LOCAL_RELAYER`는 `BLOCKCHAIN_RELAYER_PRIVATE_KEY`로 raw transaction을 서명한다.
- 현재 등록된 relayer의 private key를 backend secret으로 사용할 수 없다면 자동 앵커링은
  시작할 수 없다.
- 이 경우 새 Kairos relayer 키를 KMS 또는 별도 secret store에서 생성하고 KAIA를 옮긴 뒤,
  deployer가 새 주소에 `RELAYER_ROLE`을 부여해야 한다.
- Chrome 확장 지갑을 서버의 자동 relayer로 직접 사용하는 구조는 아니다.

Issuer 공개 주소를 백엔드 원장에 동기화할 때 `signerRef`에는 private key가 아니라 KMS나
외부 signer에서 키를 찾는 관리용 참조만 저장한다.

## 6. 실패와 복구

### 승인 직후 화면을 새로고침한 경우

새 Registry를 배포하지 않는다. 배포 콘솔이 브라우저 저장소의 tx hash와 예상 주소로 receipt를
복구하게 둔다. Kaiascan과 RPC에서 기존 tx 상태를 먼저 확인한다.

### 트랜잭션 상태가 불명확한 경우

동일 작업을 즉시 다시 보내지 않는다.

```text
tx hash 존재
-> receipt 조회
-> 성공이면 event와 readback 확인
-> pending이면 기다림
-> 실패면 실패 사유를 확인한 뒤 재시도 여부 결정
```

### Issuer 서명 복구 주소가 다른 경우

등록을 중단한다. 다음을 확인한다.

- Wallet에서 선택한 계정
- `ISSUER_SIGNER_ADDRESS`
- EIP-712 domain의 chain ID와 Registry
- Kaia account key가 `AccountKeyLegacy`인지
- 역할 기반 또는 decoupled key인지

이미 잘못된 signer를 같은 key version에 등록했다면 덮어쓰지 않는다. 새 key version을
등록하고 DB도 그 버전으로 동기화한다.

### 잘못된 Registry를 배포한 경우

V1은 proxy upgrade를 사용하지 않는다. 기존 코드를 바꾸지 말고 수정된 새 contract version을
새 주소에 배포한다. 기존 Credential 검증을 유지하려면 chain/contract registry 또는 router를
먼저 준비해야 한다.

## 7. 이번 배포 이후 남은 작업

- [ ] Kaiascan source-code verification
- [ ] 백엔드를 `READ_ONLY`로 연결하고 contract/issuer readback 시험
- [ ] 자동 전송 가능한 relayer secret 또는 KMS signer 준비
- [ ] 테스트 학교의 `ORGANIZATION.publicId`를 현재 issuer public ID와 일치시키거나 새 issuer 등록
- [ ] `ORGANIZATION.publicId`와 온체인 issuer key 동기화
- [ ] 테스트 Credential 1건 발급
- [ ] Credential batch 생성과 실제 issuer EIP-712 승인
- [ ] Relayer anchor transaction 및 receipt/readback 검증
- [ ] 공개 검증 API와 Portable Package 검증
- [ ] Credential revoke 또는 supersede 통합 시험
- [ ] 장애 복구와 DB/Object Storage 백업 훈련

이 목록이 끝나기 전에는 Kaia Mainnet `8217`로 전환하지 않는다.

## 8. 저장소 기준 파일

| 파일 | 역할 |
| --- | --- |
| `contracts/deployments/kairos-1001.json` | 프로그램이 읽을 수 있는 공개 배포 좌표 |
| `contracts/.env` | Git에 포함되지 않는 로컬 실행 설정 |
| `contracts/wallet-deployer/` | Kaia Wallet 배포 콘솔 |
| `contracts/scripts/serve-wallet-deployer.mjs` | loopback-only 정적 서버 |
| `docs/blockchain-implementation-runbook.md` | Credential부터 worker까지 전체 실행 가이드 |
| 이 문서 | 실제 배포 증적과 재현 절차 |
