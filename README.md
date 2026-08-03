# Trekkey API

대학생 공모전 탐색과 참여를 지원하는 **Trekkey** 서비스의 Spring Boot REST API입니다.

현재 `main` 브랜치는 사용자 인증, 학교 검색, Credential 생성·Merkle 배치·Kaia 앵커링 기반을 제공합니다. 대회·팀·제출·수상 업무 도메인이 합쳐지면 확정 서비스에서 내부 Credential 발급 경계를 호출하도록 구성되어 있습니다.

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
- Transactional Outbox 기반 relayer 및 receipt/readback 검증
- 지갑 없이 사용하는 공개 Credential 검증 API
- 공통 성공·오류 응답과 전역 예외 처리
- Swagger/OpenAPI 문서

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.14 |
| Security | Spring Security, JWT (`jjwt` 0.13.0) |
| Data | Spring Data JPA, QueryDSL, MySQL |
| Blockchain | Kaia, Solidity 0.8.28, OpenZeppelin 5.4.0, web3j 4.14.0 |
| Integrity | RFC 8785 JCS, Unicode NFC, SHA-256, Keccak-256, Merkle proof, EIP-712 |
| Validation | Jakarta Bean Validation |
| API Docs | springdoc-openapi / Swagger UI |
| Build & Test | Gradle, JUnit 5 |

## 프로젝트 구조

```text
src/main/java/com/api/trekkey
├── domain
│   ├── auth          # 로그인, 토큰 발급·재발급·로그아웃
│   ├── credential    # Credential, Merkle, EIP-712, Kaia adapter와 worker
│   ├── organization  # 학교 검색
│   └── user          # 사용자 엔티티와 회원가입 DTO
└── global
    ├── config        # Security, QueryDSL, Web 설정
    ├── exception     # 공통 예외 처리
    ├── response      # 공통 API 응답
    ├── security      # JWT 인증 필터와 핸들러
    └── swagger       # OpenAPI 설정
```

## API 요약

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/signup` | 사용자 회원가입 | 불필요 |
| `POST` | `/api/auth/signin` | 로그인 및 토큰 발급 | 불필요 |
| `POST` | `/api/auth/refresh` | access/refresh token 재발급 | refresh cookie |
| `POST` | `/api/auth/logout` | 로그아웃 및 refresh token 폐기 | refresh cookie |
| `GET` | `/api/organizations?keyword=` | 활성 학교 검색 | 불필요 |
| `GET` | `/api/public/credentials/{publicId}` | hash·proof·Kaia 상태 공개 검증 | 불필요 |
| `POST` | `/api/admin/blockchain/issuer-keys/{version}/sync` | 온체인 학교 key 동기화 | `ADMIN` |
| `POST` | `/api/admin/blockchain/batches` | READY Credential Merkle batch 생성 | `ADMIN` |
| `GET/POST` | `/api/admin/blockchain/batches/{publicId}/approval` | 학교 EIP-712 승인 조회·제출 | `ADMIN` |
| `POST` | `/api/admin/blockchain/batches/{publicId}/approval/renew` | 만료·실패한 batch 승인 갱신 | `ADMIN` |
| `POST` | `/api/admin/blockchain/batches/{publicId}/reconcile` | 온체인 성공·로컬 실패 batch 수렴 | `ADMIN` |
| `POST` | `/api/admin/blockchain/credentials/{publicId}/status-events` | 폐기·대체 요청 | `ADMIN` |
| `GET/POST` | `/api/admin/blockchain/status-events/{id}/approval` | 상태 변경 승인 조회·제출 | `ADMIN` |
| `POST` | `/api/admin/blockchain/status-events/{id}/approval/renew` | 만료·실패한 상태 승인 갱신 | `ADMIN` |
| `POST` | `/api/admin/blockchain/status-events/{id}/reconcile` | 온체인 성공·로컬 실패 상태 수렴 | `ADMIN` |

상세 요청·응답 스키마는 애플리케이션 실행 후 Swagger UI에서 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 실행 환경

다음 도구가 필요합니다.

- JDK 21
- MySQL

환경별 설정은 `src/main/resources/application.properties` 또는 환경변수로 주입합니다.

| 환경변수 | 설명 | 기본값 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | 없음 |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자명 | 없음 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | 없음 |
| `JWT_SECRET` | Base64 인코딩된 64바이트 이상 JWT 서명 키 | 없음 |
| `JWT_ACCESS_EXPIRATION` | access token 유효기간(초) | `1800` |
| `JWT_REFRESH_EXPIRATION` | refresh token 유효기간(초) | `1209600` |
| `JWT_REFRESH_COOKIE_NAME` | refresh cookie 이름 | `refreshToken` |
| `JWT_REFRESH_COOKIE_SECURE` | HTTPS 전용 쿠키 여부 | `false` |
| `JWT_REFRESH_COOKIE_SAME_SITE` | SameSite 정책 | `Lax` |
| `BLOCKCHAIN_ANCHORING_MODE` | `DISABLED`, `READ_ONLY`, Kairos 전용 `LOCAL_RELAYER` | `DISABLED` |
| `BLOCKCHAIN_CHAIN_ID` | Kaia chain ID | `1001` |
| `BLOCKCHAIN_RPC_URL` | Trekkey가 Kaia 노드에 조회·전송을 요청하는 EVM JSON-RPC endpoint. 자체 노드를 실행한다는 뜻이 아님 | Kairos Testnet public RPC |
| `BLOCKCHAIN_CONTRACT_ADDRESS` | 배포한 registry 주소 | 없음 |
| `BLOCKCHAIN_RUNTIME_CODE_HASH` | 승인한 registry runtime Keccak-256 | 없음 |
| `BLOCKCHAIN_WORKER_ENABLED` | Outbox/receipt worker 실행 | `false` |
| `BLOCKCHAIN_OUTBOX_LEASE_TIMEOUT` | 중단된 worker 작업 회수 시간 | `1m` |
| `BLOCKCHAIN_RELAYER_PRIVATE_KEY` | Kairos 개발용 relayer key | 없음 |

> `JWT_SECRET`이 없거나 HS512 기준보다 짧으면 애플리케이션이 기동하지 않습니다. HTTPS 환경에서는 `JWT_REFRESH_COOKIE_SECURE=true`를 사용하세요.

`BLOCKCHAIN_RPC_URL`의 요청 흐름, public RPC와 자체 Endpoint Node의 차이, EC2 권장 사양은
[Kaia RPC와 EC2 배포 기준](./docs/blockchain-rpc-and-ec2.md)을 참고하세요.

## 로컬 실행

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/trekkey'
export SPRING_DATASOURCE_USERNAME='root'
export SPRING_DATASOURCE_PASSWORD='your-password'
export JWT_SECRET="$(openssl rand -base64 64)"

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

## 관련 문서

- [업무·블록체인 전체 통합 ERD](docs/unified-erd.md)
- [팀 회의용 Mermaid 다이어그램 보드](docs/architecture-diagrams.md)
- [공모전·Credential 최종 ERD](docs/erd.md)
- [Credential 및 Kaia 앵커링 설계](docs/blockchain-anchoring-architecture.md)
- [블록체인 구현 및 Kairos 실행 가이드](docs/blockchain-implementation-runbook.md)
- [2026-07-28 Kairos Registry 배포 기록과 재현 절차](docs/blockchain-kairos-deployment.md)
- [Kairos 지속 사용 및 후속 개발 인계](docs/blockchain-kairos-continuation-plan.md)
- [2026-07-27 통합 회의 안건](docs/meetings/2026-07-27-integration-agenda.md)
- [설계 문서 인덱스](docs/README.md)
