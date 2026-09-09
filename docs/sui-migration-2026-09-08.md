# Sui 이식 — 초기 구현 계약과 후속 통합

초기 구현 기준은 2026-09-08, 백엔드 `e113959`, 프론트 `946525e`였다. 이후 사용자 요청으로
백엔드를 실제 운영 기준 `0615984`까지 fast-forward하고 Sui 변경을 재적용했다. 최초 전체 백업과
추가 백업, 기존 dirty 변경·autostash를 보존했다. 최신 결과는 [전체 통합 기록](./sui-full-integration-2026-09-08.md)을 우선한다.
**코드 구현·격리 테스트넷 검증과 운영 이관은 별개다.**

## 1. 범위와 결정

초기 사용자 승인: 두 저장소를 먼저 백업하고 Kaia 블록체인 의존부를 Sui로 이식하며 프론트는 유지한다.
후속으로 증빙 제출 뒤 폼 초기화와 팀 최대인원 고정 오류 두 곳만 수정 승인을 받았다. 디자인·CSS는 유지했다.
최신 통합 검증에서 과목 조회/수정 트랜잭션과 DB 시간 정밀도 문제도 최소 보완했다.

- 유지: 업무 발급 흐름, canonical JSON, schema profiles, SHA-256, Merkle V1, 기존 HTTP 경로·상태명·JSON envelope,
  PDF/ZIP 바이너리 응답, 학교 승인 후 Outbox 전달, 원문 비공개 경계.
- 추가: Sui Move Registry, 현재 Sui TypeScript SDK 기반 로컬 게이트웨이, Spring Sui adapter,
  Sui 전용 승인 도메인, 주소/트랜잭션/checkpoint 표현, 불변 네트워크 문맥, 수동 DB migration.
- 초기 제외: 프론트 지갑·화면·문구 변경, 기존 개인정보 공개 정책 변경, Walrus/Seal 파일 저장·복호화,
  MemWal/AI, 원격 Git 병합·push, 운영 DB 실행, 공개 체인 배포, 실사용 키 접근.
- 후속 승인으로 진행한 범위: 최신 운영 ref 통합, 합성 Sui 테스트넷 배포·거래, 지정 EC2 격리 테스트,
  운영 백업·읽기 전용 현황 조회·오프라인 복원 검증. 메인넷·실사용 지갑 키·Git push는 실행하지 않았다.
- 운영 대체는 조건부 승인됐으나 기존 Kairos 수상 증명 6개의 검증 경로를 단일 Sui 서버가 보존하지 못하므로 실행하지 않았다.

Walrus/Seal을 이름만 설정에 추가하고 개인정보 보호가 구현됐다고 주장하지 않는다.
이 이식의 암호화 범위는 **증명 무결성과 기관 승인**이며, 개인정보 접근 제어 전체를 대체하지 않는다.

## 2. 백업과 복구

저장소 상위 `backups/sui-migration-20260908.i0J0tQ/trekkey-before-sui.tar.gz`에 프론트·백엔드 전체를 백업했다.
Git 이력, 미커밋/미추적 파일, ignored 파일을 포함하며 비밀 설정이 있을 수 있으므로 외부 업로드 금지.

- SHA-256: `3b5da7ac822c63b420c8477da0350a7288a1a2d4bb6e52f0529a28e6882a1dd6`
- 압축 목록 읽기: 49,445개 entry.
- 운영 DB·외부 파일 저장소·기존 체인 상태의 백업은 **아니다**.
- 복원은 새 빈 폴더에 먼저 풀고 현재 작업과 비교한다. 현재 디렉터리에 그대로 덮어쓰지 않는다.

Move CLI 최초 테스트가 자동 생성한 미사용 개발 설정 5개는 즉시 별도 `unused-cli-config/`로 보존 이동했다.
새로 생긴 빈 사용자 `.sui` 폴더는 제거하여 이전 상태로 되돌렸다. 해당 개발 키는 사용하지 않는다.
이후 Move 검증에는 명시적인 임시 `SUI_CONFIG_DIR`와 `MOVE_HOME`, 키 없는 설정을 사용한다.

## 3. 실제 연결

학교 관리자 승인 → 기존 Java 서비스 → Outbox → Sui adapter → 인증된 Sui SDK gateway → Move Registry.

게이트웨이 준비 작업은 transaction bytes를 만들고 relayer로 서명하지만 **전송하지 않는다**.
Java가 `signed_raw_transaction`에 버전 있는 envelope와 native digest를 먼저 저장·commit한 후 `/v1/broadcast`를 호출한다.
응답 유실은 `UNKNOWN`으로 남기고 동일한 bytes/signature/digest만 재사용한다. 완료는 receipt의 성공·정확한 이벤트와
Registry read-back을 모두 대조한 뒤 기존 `ANCHORED/REVOKED/SUPERSEDED` 상태로 수렴시킨다.

Sui에는 EVM nonce가 없으므로 `tx_nonce=NULL`. 32-byte relayer 주소와 package ID를 자르거나 20-byte로 위장하지 않는다.
기관의 `signer_address`는 기존 secp256k1 서명을 식별하는 **20-byte 기관 키 ID**이며 Sui transaction sender와 다르다.

Sui 가스 object version의 중복 서명 방지는 게이트웨이의 영속 준비 journal에서 처리한다.
동일 승인 digest는 재시작 후에도 동일 transaction을 반환해야 하고, 다른 승인은 예약된 같은 gas reference로 서명할 수 없다.
운영에서는 journal의 영속 볼륨·디스크 오류·복구를 별도로 검증해야 한다. 임시 저장소나 임의 TTL 삭제는 운영 구성이 아니다.

## 4. 승인/증명 호환 규격

프론트는 `0x`+130 hex 기관 서명만 받는다. 따라서 Sui 지갑 serialized signature를 위장하지 않고
기존 **low-S secp256k1 `r || s || v` (65 bytes, v=27/28)** 기관 승인을 유지한다.
Sui 거래 서명은 게이트웨이의 별도 Ed25519 relayer 키가 맡는다.

신규 승인 규격:

```
domainHash = keccak256(UTF8("TREKKEY_SUI_APPROVAL_V1")
                     || chainIdentifier[4] || originalPackageId[32] || registryId[32])
digest = keccak256(0x1901 || domainHash || existingApprovalStructHash)
```

기존 batch/status struct encoding과 Merkle V1은 바꾸지 않는다. 도메인에 원본 Move package ID와 registry object ID를 모두 포함한다.
Registry의 고정 network ID와 실제 RPC의 chain identity를 게이트웨이가 대조한다.
승인은 체인·package·registry·유형·nonce·deadline·내용이 바뀌면 재사용할 수 없다.

HTTP 필드 이름 `typedDataJson`은 유지하지만 Sui 값은 명시적 `scheme=TREKKEY_SUI_APPROVAL_V1`인 JSON **문자열**이다.
일반 EIP-712 지갑의 `signTypedData`에 넣지 않는다. 기존 Kaia 승인 payload는 여전히 EIP-712 그대로다.
공통 fixture: [`approval-v1.json`](../contracts-sui/test-fixtures/approval-v1.json).

## 5. 프론트를 그대로 둘 때의 제한

기존 공개·관리자 경로, 상태 문자열, boolean, 필드명, PDF/ZIP 응답은 유지한다. native 메타데이터는
`evidence.blockchain` 및 ZIP의 `anchor.json.blockchain`에 추가한다.

| 기존 필드 | Sui에서의 의미 |
| --- | --- |
| `chainId` | `0`: EVM chain ID가 없다는 호환 sentinel. 1001/8217로 위장하지 않음 |
| `contractAddress` | 32-byte 원본 Move package ID |
| `transactionHash` | Sui native base58 transaction digest |
| `blockNumber` | 실제 checkpoint sequence |
| `blockchain` | provider/network/실제 chainIdentifier/packageId/registryObjectId/transactionDigest/checkpoint/explorerUrl/approvalScheme |

프론트는 추가 메타데이터를 아직 사용하지 않는다. 따라서 Sui 탐색기 링크, 정확한 Sui 네트워크 이름,
하드코딩된 Kaia/EIP-712 문구는 개선되지 않는다. Live Tamper Lab은 `Number(chainId)>0`를 요구하므로 Sui에서는
기존 최종 `verified` 플래그가 false다. 이를 피하려고 가짜 양수 chainId나 조작된 검증 성공을 반환하지 않는다.
일반 공개 검증 API의 실제 상태와 Merkle 증거는 제공된다. 프론트 전체 Sui UX 완료로 보고하지 않는다.

## 6. 기존 원장 보호와 DB 이관

[`2026-09-08-sui-anchor-context.sql`](migrations/2026-09-08-sui-anchor-context.sql)은 수동 검토용이며 자동 실행하지 않는다.
`chain_context`를 batch/key/status/transaction에 추가하고 contract/relayer 주소만 VARBINARY(32)로 넓힌다.
과거 원문·hash·signature·chainId는 바꾸지 않는다. 역사 행의 NULL context는 **Kaia로만** 해석한다.

- 배치 생성/키 동기화/상태 요청 때 실제 네트워크 문맥을 고정한다.
- 다른 문맥의 승인·갱신·복구·전송·검증은 거부한다. 설정 변경만으로 옛 증명을 새 체인으로 재발급하지 않는다.
- 기존 기관 키 버전은 보존한다. Sui에는 각 학교에서 아직 사용하지 않은 새 `keyVersion`을 등록·동기화한다.
- 기존 Kaia 증명은 별도 legacy READ_ONLY 배포/설정으로 계속 검증하거나 명시적인 재발급 정책을 수립해야 한다.
  단일 서버가 모든 과거/새 체인을 자동 라우팅하는 기능은 이번 범위가 아니다.
- 공개 패키지 좌표는 현재 설정보다 저장된 transaction/context를 우선한다.
- 수동 이관 전에 옛 미완료 work를 정리하고 worker를 끈다. 신·구 worker를 동일 DB에서 임의 동시 활성화하지 않는다.
- rollback은 이전 코드/설정으로 되돌리고 확장 컬럼을 유지한다. Sui 행 생성 후 주소 컬럼을 20-byte로 축소하지 않는다.

## 7. 실행 설정 — 운영 적용은 별도 승인

Spring: `SPRING_PROFILES_ACTIVE=sui` 또는 `BLOCKCHAIN_PROVIDER=SUI`를 명시한다.
기본 mode는 계속 `DISABLED`, worker는 false다. 첫 연결은 `READ_ONLY`로 시작한다.

| 환경값 | 용도 |
| --- | --- |
| `SUI_NETWORK` | `localnet` / `testnet` / 읽기 전용 `mainnet` |
| `SUI_CHAIN_IDENTIFIER` | 실제 genesis에서 얻은 8자리 lowercase hex |
| `SUI_PACKAGE_ID`, `SUI_REGISTRY_ID` | 독립 확인한 배포 manifest의 32-byte IDs |
| `SUI_GATEWAY_URL` | Java→gateway, 기본 `http://127.0.0.1:9187` |
| `SUI_GATEWAY_TOKEN` | Java/gateway 공유 인증 토큰. 로그·argv·Git에 노출 금지 |
| `SUI_GRPC_URL` | gateway→Sui endpoint. localnet은 loopback, 원격은 HTTPS |
| `SUI_RELAYER_PRIVATE_KEY` | gateway 전용 개발 relayer. 기관 승인 키와 분리 |
| `SUI_GATEWAY_JOURNAL_DIR` | gateway 준비/가스 예약 영속 저장 경로 |
| `BLOCKCHAIN_ANCHORING_MODE` | DISABLED → READ_ONLY → 개발 LOCAL_RELAYER |
| `BLOCKCHAIN_WORKER_ENABLED` | 검증·migration·복구 준비 전에는 false |

mainnet 로컬 키 쓰기는 Java와 게이트웨이 양쪽에서 차단한다. 운영 KMS/HSM, 관리 권한, 키 회전·침해 복구,
gas 예산·잔액·journal 디스크/backup, RPC SLA는 별도 출시 gate다.
기존 Kaia 배포 workflow/compose에 Sui 서비스를 자동 끼워 넣지 않았다. 게이트웨이를 어디에 어떻게 운영할지 결정하기 전 배포하지 않는다.

학교 승인 도구는 [`sui-gateway`](../sui-gateway/README.md)의 `sign-approval`을 사용한다.
학생/사용자는 새 지갑이나 코인이 필요 없다. 도구는 독립 배포 manifest와 예상 기관 signer를 대조한 후
비공개 file descriptor로 읽은 기관 키로 승인하며, 직접 chain transaction을 보내지 않는다.

## 8. 검증 범위와 남은 gate

2026-09-08 실제 실행 결과:

| 검증 | 결과 |
| --- | --- |
| Java 전체 fresh `test bootJar --offline --rerun-tasks` | 96 suites, 688개 중 **663 통과 / 25 skip / 실패 0**, 실행 JAR 빌드 성공 |
| Java skip 범위 | 명시적 전용 MySQL 테스트 환경이 필요한 9개 class의 25개 테스트. MySQL 검증 완료로 계산하지 않음 |
| Move `build/test --warnings-are-errors` | 생산 build 성공, **42/42 통과** |
| Sui gateway | 실제 SDK crypto/BCS, HTTP, journal·Clock·배포·launcher 회귀 **53/53 통과**, TypeScript build 성공 |
| 기존 Solidity | **12/12 통과**, TypeScript type-check 통과. Node 25에 대한 Hardhat 미지원 경고는 남음 |
| 프론트 | 기존 **19/19 통과**. 백업 복원본과 내용 비교 일치. 프로덕션 build도 복원본에서 성공 |
| 실제 임시 Sui localnet | Move publish → Registry/기관 키/relayer 등록 → HTTP 발급·폐기 → checkpoint/BCS 조회 성공 |
| 실제 공개 Sui testnet | 격리 서버에서 계약 배포/등록, Java·H2·실제 gateway·testnet 발급/취소 **1/1 E2E PASS**, `VALID`→`REVOKED`, 5개 거래 모두 성공 |
| 별도 서버 MySQL | 새 schema 0→30 tables bootstrap 후 `ddl-auto=validate` 기동 및 API HTTP 200. 전체 MySQL lifecycle·기존 데이터 이관은 미검증 |

Java 테스트에는 실제 비밀값 대신 합성 JWT 만료값·localhost frontend/CORS·임시 upload 경로를 제공했다.
H2 저장 검증은 통과했지만 MySQL DDL·실운영 데이터 검증을 대체하지 않는다. 프론트는 build를 위해서도 원본을 수정하지 않았다.

로컬넷에서는 준비된 envelope를 caller 쪽에 `fsync` 저장한 뒤 전송했고, 같은 승인 재요청이 동일 bytes를 반환하는 것도 확인했다.
검증망 `chainIdentifier=bdf37bca`; 발급 digest `D4MW27fDnqzb3uq9URbExYHc3wS6wVjVfFtr8hgn1A9S` (checkpoint 1295),
폐기 digest `AwbnDonJWoG6NBJYcDLrVWtsnnt9iciNeHet5BHyq3NS` (checkpoint 1301), 최종 상태 `REVOKED`.
이 digest들은 종료된 임시 로컬 체인의 기록이며 공개 탐색기에서 조회할 수 없다. 원본 결과는 작업 임시 경로
`/private/tmp/trekkey-sui-localnet.BE72Eb/smoke2/smoke-result.json`에 보존했다. 모든 listener는 종료·포트 해제를 확인했다.

첫 로컬넷 실행에서는 host 현재초 기반 상태 변경 준비가 실패하여 `BUILDING` claim이 남았고, 확인된 chain timestamp로 바꾼 재실행은 성공했다.
정확한 Move abort는 수집되지 않아 Clock 시차는 추정이다. claim 이후 불명확한 build 실패를 자동 삭제/재서명하지 않는 정책은 유지한다.
이후 gateway는 canonical 공유 Clock(`0x6`)을 읽어 시간 조건을 사전검사하도록 보완했다. 아직 미래인 `effectiveAt`은 claim 생성 전에
재시도 가능한 `BLOCKCHAIN_CHAIN_CLOCK_BEHIND`로 거절하며, 체인 시간 진행 후 같은 승인으로 성공하는 회귀를 추가했다.
위 발급·폐기 전체 스모크는 이 마지막 보완 직전의 결과다.
마지막 보완의 실제 gRPC 읽기는 동일 로컬넷을 잠깐 재기동해 별도로 확인했다: canonical shared `0x6`의
`GrpcChain.clockSeconds()=1788852303`, 독립 JSON `timestamp_ms=1788852303760`으로 정수 초 변환이 일치했다.
그 순간 host는 `1788852304`초여서 실제 1초 차이도 관측했다. 이 확인은 추가 서명/거래 없는 읽기이며 서버는 다시 종료했다.

아직 별도 검증이 필요한 것: 실제 MySQL 전체 lifecycle/upgrade/rollback, 운영 데이터 이관, 실제 대학 키·권한 승인,
mainnet 배포, 브라우저-백엔드 통합 E2E, 부하/장애 복구, 독립 계약 보안감사.
로컬넷 smoke를 실행하더라도 공개 testnet 배포나 운영 준비 완료의 근거가 아니다.

원문 개인정보/동의/공개 링크 정책과 기존 XSS/학사 평가 오류는 이식으로 자동 해결되지 않는다.
학교가 입력한 사실의 진실성, 상태 변경 대상의 Merkle 포함 증명, 이미 공유한 문서 회수도 이번 계약이 보증하지 않는다.

## 9. 공식 기술 근거

후속 실제 실행은 [테스트넷 서버 격리 실행 기록](sui-testnet-server-runbook.md#8-2026-09-08-승인-후-실제-실행-결과)을 따른다.
격리 공개 testnet 배포·발급·취소 검증을 완료했으며 운영 교체와 프론트 변경은 하지 않았다.

- [Sui SDK clients](https://sdk.mystenlabs.com/sui/clients): SuiGrpcClient, 기존 JSON-RPC의 deprecation.
- [Sui 설치/검증](https://docs.sui.io/getting-started/onboarding/sui-install): 검증용 CLI. 사용자 기본 설정 자동 생성에 주의.
- [Walrus 데이터 보안](https://docs.wal.app/docs/data-security): 저장소 자체는 공개이며 비밀성은 별도 암호화가 필요함.
