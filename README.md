# Trekkey API

대학 공모전 운영에서 확정된 활동을 검증 가능한 Credential로 발급하고, 공개 검증·졸업요건 자가점검·외부 증빙 검수를 연결하는 **Trekkey** Spring Boot REST API입니다.

코드에서 다시 산출한 전체 구현 상태, 프론트 33개 route, 백엔드 106개 고유 HTTP operation과 release gate는 [문서 허브](docs/README.md)에서 관리합니다. 상세 설계가 있다는 이유만으로 구현 완료로 표시하지 않습니다.

2026-09-08 현재 백엔드는 `HEAD=0615984`에 Sui 이식·통합 수정과 기존 사용자 변경을 더한 working tree입니다. 최신 결과의 정본은 [전체 통합 검증·운영 인계](docs/sui-full-integration-2026-09-08.md)입니다. 기존 운영 Kaia 증명 6개를 단일 Sui 설정에서 검증할 수 없어 **운영 교체는 보류하고 기존 서비스를 유지**합니다.

## 주요 기능

- 일반 사용자 회원가입
- 이메일·비밀번호 로그인
- JWT access token 발급
- HttpOnly 쿠키 기반 refresh token rotation
- refresh token 재사용 감지 및 토큰 패밀리 폐기
- 로그아웃 및 refresh token 폐기
- 활성 학교 목록 검색
- 참여·작품·수상 Credential canonical JSON 및 hash 생성
- OpenZeppelin 호환 Merkle batch와 EIP-712 학교 승인
- Kaia `TrekkeyCredentialRegistryV1` 앵커링, 폐기, 대체
- Sui Move Registry·SDK gateway·Java adapter와 전용 기관 승인 도메인 — 격리 영속 MySQL→testnet 3종 발급·취소 검증 PASS, 기존 Kaia 자동 라우팅 없음
- Transactional Outbox 기반 relayer 및 receipt/readback 검증
- 지갑 없이 사용하는 공개 Credential 검증 API
- QR 인증서 PDF와 공개 proof package ZIP
- UUID 기반 학생 공개 활동 프로필 on/off·link rotation
- 일부 한성대학교 정책 기반 비공식 졸업요건 자가점검 MVP
- 성적표·활동 import preview와 명시적 적용
- 외부 증빙 bundle 제출과 서로 다른 관리자 2인의 L2 수동검수 MVP
- 공통 성공·오류 응답과 전역 예외 처리
- Swagger/OpenAPI 문서

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.14 |
| Security | Spring Security, JWT (`jjwt` 0.13.0) |
| Data | Spring Data JPA, QueryDSL, MySQL |
| Blockchain | Kaia, Solidity 0.8.28, OpenZeppelin 5.4.0, web3j 4.14.0; Sui Move·TypeScript SDK gateway |
| Integrity | RFC 8785 JCS, Unicode NFC, SHA-256, Keccak-256, Merkle proof, EIP-712 |
| Validation | Jakarta Bean Validation |
| API Docs | springdoc-openapi / Swagger UI |
| Build & Test | Gradle, JUnit 5 |

## 프로젝트 구조

```text
src/main/java/com/api/trekkey
├── domain
│   ├── auth          # 로그인, 토큰 발급·재발급·로그아웃
│   ├── credential    # Credential, Merkle, Kaia/Sui 승인·adapter·worker
│   ├── organization  # 학교 검색
│   ├── contest/team/submission/review/award # 공모전 운영
│   ├── graduation    # 한성대 졸업 자가점검 MVP
│   ├── evidence      # 외부 증빙 L2 수동검수 MVP
│   └── user          # 사용자 엔티티와 회원가입 DTO
└── global
    ├── config        # Security, QueryDSL, Web 설정
    ├── exception     # 공통 예외 처리
    ├── response      # 공통 API 응답
    ├── security      # JWT 인증 필터와 핸들러
    └── swagger       # OpenAPI 설정
```

## 대표 API 요약

아래는 진입용 대표 목록이며 전수가 아닙니다. 전체 106개 method+path와 접근 역할·상태는 [HTTP API 카탈로그](docs/spec/api-catalog.md)를 사용합니다.

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/signup` | 사용자 회원가입 | 불필요 |
| `POST` | `/api/auth/signin` | 로그인 및 토큰 발급 | 불필요 |
| `POST` | `/api/auth/refresh` | access/refresh token 재발급 | refresh cookie |
| `POST` | `/api/auth/logout` | 로그아웃 및 refresh token 폐기 | refresh cookie |
| `GET` | `/api/organizations?keyword=` | 활성 학교 검색 | 불필요 |
| `GET` | `/api/public/credentials/{publicId}` | hash·proof·설정된 체인의 상태 공개 검증 | 불필요 |
| `GET` | `/api/public/credentials/{publicId}/package` | 공개 proof package ZIP | 불필요 |
| `GET` | `/api/public/credentials/{publicId}/certificate` | QR 인증서 PDF | 불필요 |
| `GET/PUT` | `/api/me/public-activity-profile` | 공개 활동 프로필 설정 | `PARTICIPANT` |
| `POST` | `/api/me/graduation/evaluations` | 비공식 졸업요건 자가점검 | `PARTICIPANT` |
| `POST` | `/api/me/evidence-submissions` | 외부 증빙 bundle 제출 | `PARTICIPANT` |
| `POST` | `/api/admin/evidence-verifications/{caseId}/reviews` | L2 관리자 수동검수 | `ADMIN` |
| `POST` | `/api/admin/blockchain/issuer-keys/{version}/sync` | 온체인 학교 key 동기화 | `ADMIN` |
| `POST` | `/api/admin/blockchain/batches` | READY Credential Merkle batch 생성 | `ADMIN` |
| `GET/POST` | `/api/admin/blockchain/batches/{publicId}/approval` | 학교 승인 조회·제출: Kaia EIP-712 / Sui 전용 도메인 | `ADMIN` |
| `POST` | `/api/admin/blockchain/batches/{publicId}/approval/renew` | 만료·실패한 batch 승인 갱신 | `ADMIN` |
| `POST` | `/api/admin/blockchain/batches/{publicId}/reconcile` | 온체인 성공·로컬 실패 batch 수렴 | `ADMIN` |
| `POST` | `/api/admin/blockchain/credentials/{publicId}/status-events` | 폐기·대체 요청 | `ADMIN` |
| `GET/POST` | `/api/admin/blockchain/status-events/{id}/approval` | 상태 변경 승인 조회·제출 | `ADMIN` |
| `POST` | `/api/admin/blockchain/status-events/{id}/approval/renew` | 만료·실패한 상태 승인 갱신 | `ADMIN` |
| `POST` | `/api/admin/blockchain/status-events/{id}/reconcile` | 온체인 성공·로컬 실패 상태 수렴 | `ADMIN` |

상세 요청·응답 스키마는 API 문서를 명시적으로 활성화한 개발 환경에서 확인할 수 있습니다.
운영 기본값은 공개 공격 표면을 줄이기 위해 API 문서와 Swagger UI 모두 비활성화입니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- 로컬 활성화: `SPRINGDOC_API_DOCS_ENABLED=true`, `SPRINGDOC_SWAGGER_UI_ENABLED=true`

## 실행 환경

다음 도구가 필요합니다.

- JDK 21
- MySQL

환경별 설정은 `src/main/resources/application.properties` 또는 환경변수로 주입합니다.

| 환경변수 | 설명 | 기본값 |
| --- | --- | --- |
| `DATASOURCE_URL` | MySQL JDBC URL | 없음 |
| `DATASOURCE_USERNAME` | DB 사용자명 | 없음 |
| `DATASOURCE_PASSWORD` | DB 비밀번호 | 없음 |
| `JWT_SECRET_KEY` | Base64 인코딩된 64바이트 이상 JWT 서명 키 | 없음 |
| `JWT_ACCESS_EXPIRATION` | access token 유효기간(초) | 필수 주입; 격리 testnet은 `900` |
| `JWT_REFRESH_EXPIRATION` | refresh token 유효기간(초) | 필수 주입; 격리 testnet은 `604800` |
| `EVIDENCE_LOOKUP_HMAC_SECRET` | 증빙 번호 조회용 전용 HMAC secret | 코드에는 JWT key fallback이 남음; 격리 runtime에서는 별도 secret 주입 |
| `JWT_REFRESH_COOKIE_NAME` | refresh cookie 이름 | `refresh` |
| `JWT_REFRESH_COOKIE_SECURE` | HTTPS 전용 쿠키 여부 | `true` |
| `JWT_REFRESH_COOKIE_SAME_SITE` | SameSite 정책 | `Lax` |
| `SPRINGDOC_API_DOCS_ENABLED` | OpenAPI JSON 활성화 여부 | `false` |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | Swagger UI 활성화 여부 | `false` |
| `BLOCKCHAIN_PROVIDER` | `KAIA` 또는 명시적 `SUI` | `KAIA` |
| `BLOCKCHAIN_ANCHORING_MODE` | `DISABLED`, `READ_ONLY`, 개발망 전용 `LOCAL_RELAYER` | `DISABLED` |
| `BLOCKCHAIN_CHAIN_ID` | Kaia chain ID | `1001` |
| `BLOCKCHAIN_RPC_URL` | EVM JSON-RPC URL | Kairos public RPC |
| `BLOCKCHAIN_CONTRACT_ADDRESS` | 배포한 registry 주소 | 없음 |
| `BLOCKCHAIN_RUNTIME_CODE_HASH` | 배포 manifest의 runtime bytecode Keccak-256 | 없음 |
| `BLOCKCHAIN_WORKER_ENABLED` | Outbox/receipt worker 실행 | `false` |
| `BLOCKCHAIN_OUTBOX_LEASE_TIMEOUT` | 중단된 worker 작업 회수 시간 | `1m` |
| `BLOCKCHAIN_RELAYER_PRIVATE_KEY` | Kairos 개발용 relayer key | 없음 |

> `JWT_SECRET_KEY`가 없거나 HS512 기준보다 짧으면 애플리케이션이 기동하지 않습니다. HTTPS 환경에서는 `JWT_REFRESH_COOKIE_SECURE=true`를 사용하세요.

운영 배포에서 공개 `BLOCKCHAIN_*` 설정은 GitHub `production` Environment Variables로 관리한다. `BLOCKCHAIN_RELAYER_PRIVATE_KEY`는 GitHub Secret이나 공용 `ENV_FILE`에 넣지 않고 EC2의 `/etc/trekkey/relayer.env`에만 `root:root`, 권한 `600`으로 저장한다.

위 Kaia 운영 설정을 Sui 값으로 덮어쓰지 않습니다. Sui의 chain/package/registry identity, gateway 인증, 영속 journal과 기관별 새 key version은 [격리 실행 계약](docs/sui-full-integration-2026-09-08.md)을 따릅니다. Sui `mainnet`의 `LOCAL_RELAYER` 쓰기는 코드에서 차단합니다.

## 로컬 실행

```bash
export DATASOURCE_URL='jdbc:mysql://localhost:3306/trekkey'
export DATASOURCE_USERNAME='root'
export DATASOURCE_PASSWORD='your-password'
export JWT_SECRET_KEY="$(openssl rand -base64 64)"

./gradlew bootRun
```

애플리케이션은 기본적으로 `http://localhost:8080`에서 실행됩니다.
로컬 파일을 사용할 때는 `application-local.properties.example`을
`application-local.properties`로 복사하고 실제 값은 Git에 커밋하지 않습니다.

## 테스트

```bash
./gradlew test

cd contracts
npm ci
npm run fixture:merkle
npm test
npx tsc --noEmit
```

Java 단위·JPA 테스트는 H2를 사용한다. 별도의 MySQL 통합 테스트는 관련 환경변수가 있을 때 실행된다. Hardhat은 Node.js 20 또는 22 LTS를 권장한다.

2026-09-08 최종 Java 검증은 **760개 중 733 PASS / 27 SKIP / 실패 0, 117 suites·22초·build PASS**입니다. 별도 전용 MySQL **9개 class·25/25 PASS**, 격리 상시 HTTP **27 checks PASS**, 승인된 프론트 두 오류 수정 **29/29 PASS·production build PASS**도 확인했습니다. Java skip은 전용 MySQL 25개와 opt-in Sui 테스트 2개이며 별도 실행 결과와 합산하지 않습니다. 프론트 디자인/CSS는 변경하지 않았습니다.

영속 MySQL→Sui 전체 흐름은 첫 실행에서 2개 anchor CONFIRMED 후 WORK timestamp 반올림 오탐(TAMPERED)으로 중단됐으나, 수정한 v6로 동일 상태를 재개해 **1/1 PASS, failed 0·skip 0**를 확인했습니다. 기존 두 거래의 digest·bytes와 canonical 원문 불변 검사가 통과했고, 최종 원장은 **4 CONFIRMED·3 batches·1 status event·4 PROCESSED**입니다. PARTICIPATION은 REVOKED, WORK·AWARD는 VALID이며 runner의 PDF/ZIP 검사도 통과했습니다.

v6 산출물은 업로드 전후 해시 일치 후 격리 `18080` 서버에 적용했고, 2026-09-08 `13:02:08Z` 재시작 후 독립 HTTP에서 3종 상태·모든 claims=true와 PDF/ZIP 6개 HTTP 200·파일 시그니처를 확인했습니다. 별도 업무 readback **8 checks PASS**로 학적 프로필·3학점 과목·L2 증빙/2인 검수·파일 hash와 기존 두 거래 보존을 확인했습니다. Chrome에서는 PARTICIPATION 재조회 시 취소 상태, AWARD 조회 시 유효 수상·상격 `대상`을 실제 화면으로 확인했습니다.

AWARD 화면의 `Proof 직접 재계산`으로 진입한 live Tamper Lab에서는 원문 claims·leaf·ProofRoot 재계산이 일치했지만, 기존 Kaia 메타데이터 검사 때문에 `Kaia 공개 기록 없음`·`증거 불일치 확인 필요` 경고와 `Chain 0`이 표시됐습니다. 테스트 카드의 Java 629·프론트 19도 옛 고정 문구입니다. 일반 공개 검증 결과는 정상이지만 전체 프론트의 Sui 호환은 미완료이며, 이 UI와 기존 Kaia 증명 6개의 호환 문제가 모두 완전 대체를 막는 조건입니다. 해당 UI는 승인된 두 오류 수정 범위 밖이므로 변경하지 않았습니다.

격리 infra 도구는 fresh **19/19 PASS**, SQL guard는 11개 검사를 확인했습니다. 기존 운영은 container ID 변경 없이 HTTP 200·Kaia 수상 6개를 유지하고, 이전 격리 DB 30 tables와 full DB 48 tables도 분리해 보존했습니다. 이 결과는 단일 Sui 운영 교체나 전체 33경로/로그인 후 모든 브라우저 흐름 완주를 뜻하지 않습니다. 최신 증거는 [전체 통합 기록](docs/sui-full-integration-2026-09-08.md)을 확인합니다.

## 관련 문서

- [최신 전체 통합 검증·운영 인계](docs/sui-full-integration-2026-09-08.md)
- [Sui 최초 이식 기록과 호환 경계](docs/sui-migration-2026-09-08.md)
- [구현 기능 카탈로그](docs/implemented-features.md)
- [프로젝트 현황과 release gate](docs/project-status.md)
- [화면·라우트 명세](docs/spec/pages.md)
- [HTTP API 전수 카탈로그](docs/spec/api-catalog.md)
- [업무·블록체인 전체 통합 ERD](docs/unified-erd.md)
- [팀 회의용 Mermaid 다이어그램 보드](docs/architecture-diagrams.md)
- [공모전·Credential 최종 ERD](docs/erd.md)
- [Credential 및 Kaia 앵커링 설계](docs/blockchain-anchoring-architecture.md)
- [블록체인 구현 및 Kairos 실행 가이드](docs/blockchain-implementation-runbook.md)
- [2026-07-27 통합 회의 안건](docs/meetings/2026-07-27-integration-agenda.md)
- [설계 문서 인덱스](docs/README.md)
