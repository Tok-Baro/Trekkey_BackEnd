# Trekkey 에이전트 인수인계

후속 작업 기준은 [Sui 서비스 전환·백업 기록](./sui-service-release-2026-09-09.md)이다. 내부 앱 산출물의 EC2 전송이 재차 승인 검토에서 거절돼 중단했다. 구체적 사용자 승인 전 GitHub push/이미지 전송 등으로 우회하지 않는다. 서버 root 배포 helper는 미작성·미설치이며 현재 CD 수정안만으로 배포 가능한 상태가 아니다.

최신 후속 작업은 [기능 완성·회귀 검증](./completion-2026-09-08.md)이 정본이다. 현재 로컬 결과는 **Java 123 suites·842개=813 PASS/29 SKIP/실패 0·build PASS**, **프론트 72/72·build PASS**다. 새 JAR은 원격 전송·반영하지 않았다. 아래 v6/두 오류 한정 설명은 이전 실행 이력이다. 최신 API는 미커밋 소스를 포함해 108개이며 문서 verifier 기본 입력도 working tree다. 운영 교체 전 실제 기관 키·legacy 쓰기·DB upgrade gate는 계속 적용한다.

2026-09-08 현재: 백엔드 `HEAD=0615984`에 Sui·통합 수정과 기존 사용자 변경을 보존한 working tree다. 이전 v6 원격 실행·복구 이력은 [전체 통합 검증·운영 인계](./sui-full-integration-2026-09-08.md)가 정본이며 [최초 이식 기록](./sui-migration-2026-09-08.md)은 그 이전 단계 기록이다. 영속 MySQL→실제 Sui 3종 발급·PARTICIPATION 취소는 v6 동일 상태 재개로 PASS했고 재시작 후 독립 HTTP·업무 readback·Chrome 공개 화면도 확인했다. 당시 기존 운영 Kaia 증명 6개와 Tamper Lab의 Sui UI 호환 한계 때문에 단일 Sui 운영 교체는 하지 않았다. 해당 읽기/UI 계약은 후속 로컬 회귀에서 보완했으며, 새 JAR과 기존 원격 v6 실행을 같은 산출물로 취급하지 않는다.

## 1. 먼저 읽을 문서

1. [문서 인덱스](./README.md)
2. [구현 기능 카탈로그](./implemented-features.md)
3. [프로젝트 현황과 release gate](./project-status.md)
4. [화면·라우트 명세](./spec/pages.md)
5. [HTTP API 카탈로그](./spec/api-catalog.md)
6. [최신 기능 완성·회귀 검증](./completion-2026-09-08.md)
7. [이전 v6 전체 통합 검증·운영 인계](./sui-full-integration-2026-09-08.md)
8. 변경 영역의 정본 설계·ERD·runbook

상세 설계가 있다는 이유로 구현 완료로 승격하지 않는다. `운영 화면`, `연구 화면`, `제한 운영`, `내부 운영`, `구현·회귀 완료`, `구현 전` 여섯 상태를 기능 카탈로그의 정의대로 사용한다. `구현·회귀 완료`는 로컬 코드와 지정 회귀를 뜻하며 원격 반영·운영 릴리스·전체 브라우저 검증 완료가 아니다.

## 2. 저장소와 기준 ref

현재 백엔드 HEAD와 `origin/main`은 `0615984`다. `e113959`에서 16개 commit을 fast-forward하며 Sui/문서 변경을 보존했다. 프론트는 `946525e` 위에 v6 당시 승인된 `ExternalEvidencePanel` 비동기 form reset, `ContestApplicationForm`의 `maxTeamMembers` 수정에 이어 사용자의 전반 기능 보완 요청에 따른 수동 제출·심사위원 수정·심사 링크 복구·Sui UI·졸업 coverage와 회귀를 추가했다. 기존 dirty README와 디자인/CSS는 유지했다.

다음 표와 감사 방식은 **2026-08-30 당시의 역사 기록**이며 현재 checkout 상태가 아니다.

| 저장소 | 로컬 경로 | 2026-08-30 감사 기준 |
| --- | --- | --- |
| 프론트엔드 | sibling `../Trekkey` | `main@946525e`, `origin/main`과 일치 |
| 백엔드·contract·통합 문서 | 현재 저장소 | 최신 통합 `origin/main@0615984` |
| 백엔드 당시 checkout | 현재 저장소 | `main@e113959`, 통합 기준보다 16커밋 뒤 |

당시 인수인계는 checkout을 merge·pull하지 않고 `git show`·`git archive`로 통합 ref를 감사해 작성했다. 현재 working tree에도 사용자가 요청한 공학경진대회 보고서·문서화·Sui 변경이 있으므로 작업 전에 `git status --short`와 diff를 확인하고 덮어쓰지 않는다.

## 3. 코드 정본 위치

### 프론트

- route: `Trekkey/src/router.jsx`, `Trekkey/src/routeConfig.js`
- 역할·화면 분기: `Trekkey/src/App.jsx`
- API client: `Trekkey/src/api/*.js`
- 참가자 data load: `Trekkey/src/hooks/useParticipantData.js`
- 관리자 data load: `Trekkey/src/hooks/useAdminData.js`
- 환경·proxy: `Trekkey/src/config/env.js`, `Trekkey/vercel.json`

### 백엔드

- security: `src/main/java/com/api/trekkey/global/config/SecurityConfig.java`
- Swagger: `src/main/java/com/api/trekkey/global/swagger/SwaggerConfig.java`
- controllers: `src/main/java/com/api/trekkey/domain/**/controller/*.java`
- Credential: `src/main/java/com/api/trekkey/domain/credential/`
- graduation: `src/main/java/com/api/trekkey/domain/graduation/` — 현재 checkout에 통합됨
- evidence: `src/main/java/com/api/trekkey/domain/evidence/` — 현재 checkout에 통합됨
- contract: `contracts/contracts/TrekkeyCredentialRegistryV1.sol`
- Sui: `contracts-sui/`, `sui-gateway/`, Java `credential/infrastructure/blockchain/SuiBlockchainAnchorAdapter.java`
- 격리 실행·검증: `infra/testnet/`, `SuiPersistentWorkflowIntegrationTest`, `SuiPersistentWorkflowE2ERunner`
- migration: `docs/migrations/*.sql`

## 4. 문서 변경 계약

| 변경 | 정본 문서 | 함께 확인 |
| --- | --- | --- |
| 기능 추가·삭제·상태 변경 | `docs/implemented-features.md` | `docs/project-status.md`, `docs/README.md` |
| route·화면·API 소비 | `docs/spec/pages.md` | 프론트 README와 기능 카탈로그 |
| controller method+path | `docs/spec/api-catalog.md` | Swagger 설정, security, 프론트 API client |
| Credential·Merkle·EIP-712 | `docs/blockchain-anchoring-architecture.md` | contract README, runbook, 기능 카탈로그 |
| DB 테이블·관계 | 해당 migration과 ERD | `docs/erd.md`, `docs/unified-erd.md` |
| 졸업요건 | 최신 통합 ref의 graduation 설계·QA | API 카탈로그와 프로젝트 현황 |
| 외부 증빙 | 최신 통합 ref의 evidence 설계·QA | API 카탈로그와 프로젝트 현황 |
| 배포·키·복구 | `docs/sui-full-integration-2026-09-08.md`, 기존 Kaia runbook | 프로젝트 현황과 agent handoff |
| 출품 표현·수치 | 공학경진대회 보고서·발표 문서 | 프로젝트 현황의 테스트·공개 경계 |

Markdown가 정본이고 `docs/documentation-home.html`은 생성물이다. 생성 HTML 본문을 직접 고치지 않는다.

## 5. 감사 명령

### frontend route exact set

```bash
rg -n 'path:' ../Trekkey/src/routeConfig.js
rg -n '<Route' ../Trekkey/src/router.jsx
```

### backend controller exact set

```bash
node docs/tools/extract-spring-api.cjs
node docs/tools/extract-spring-api.cjs --ref origin/main
```

`origin/main@0615984` 기준 기대값은 controller 37개, handler 107개, 고유 method+path 106개다. 하나라도 바뀌면 API 카탈로그를 같은 변경에서 갱신한다.

### documentation reader

```bash
NODE_PATH=/path/to/bundled/node_modules node docs/tools/build-documentation-readers.cjs
node docs/tools/verify-documentation-system.cjs
```

기본 검증 대상은 현재 working tree다. `TREKKEY_DOCS_BACKEND_REF=origin/main`은 과거 ref를 명시적으로 감사할 때만 사용하며 현재 108개 operation과 혼동하지 않는다. 정확한 bundled Node 경로와 `NODE_PATH` 예시는 [문서 도구 README](./tools/README.md)에 있다.

## 6. 테스트 명령

```bash
cd ../Trekkey
npm test
npm run build

cd ../Trekkey_BackEnd
./gradlew test

cd contracts
npm test
npx tsc --noEmit
```

- Java 21이 없으면 Gradle 결과를 통과로 보고하지 않는다.
- MySQL·실제 파일·Kairos 환경 조건부 skip을 성공으로 숨기지 않는다.
- test count에는 commit, 실행일, passed·failed·skipped와 범위를 함께 기록한다.
- 브라우저 benchmark는 서버·DB·체인 TPS가 아니다.

### 2026-09-08 이전 v6 결과와 검증 한계

다음 수치·원격 실행·UI 관찰은 후속 기능 보완 전의 v6 이력이다. 현재 로컬 842개/72개 실행과 새 JAR 원격 미반영 상태는 이 문서 상단 및 최신 완료 기록을 따른다.

- Java 21 최종 run: **760개 = 733 PASS / 27 SKIP / 실패 0, 117 suites·22초·build PASS**. skip은 전용 MySQL 25개 + H2 Sui opt-in 1개 + 영속 Sui opt-in 1개다.
- 별도 실제 MySQL: **9개 class·25/25 PASS**. 위 skip을 같은 run에서 통과한 것으로 바꾸거나 중복 합산하지 않는다.
- 격리 상시 HTTP: **27 checks PASS** — 합성 3개 계정 로그인·권한, profile, 졸업 평가 4회, native MySQL 조회, PDF 증빙·2인 승인·동일 관리자 409, 성적 CSV import·course PATCH 재조회. 공식 학사 판정 검증은 아니다.
- 격리 runtime은 증빙 HMAC을 JWT secret과 분리하고 JWT TTL을 초 단위(access 900, refresh 604800)로 수정했다. 운영 구성 전체의 강제 완료로 일반화하지 않는다.
- 프론트 승인 두 수정: **29/29 PASS·production build PASS**, JSX handler/hook harness 회귀 10개 포함. React DOM/실제 브라우저 QA와 다르다.
- 영속 Sui 첫 실행 이력: anchor 2개 CONFIRMED·PARTICIPATION VALID 후 WORK timestamp 반올림 차이로 `credentialClaimsMatch=false`/TAMPERED가 발생해 중단됐다. 이 실패 기록과 원문·서명·DB·journal을 보존했다.
- v6 동일 상태 resume: **found 1 / succeeded 1 / failed 0 / skipped 0 — PASS**. 기존 두 거래의 digest·bytes와 canonical 원문 불변 검사 통과. 신규 AWARD anchor와 PARTICIPATION REVOKE 후 **4 CONFIRMED·3 batches·1 status event·4 PROCESSED**, 최종 PARTICIPATION REVOKED·WORK/AWARD VALID 및 runner의 PDF/ZIP 검사 통과.
- v6 산출물은 업로드 전후 해시 일치 후 격리 `18080`에 적용했고 `13:02:08Z`에 재시작했다. 이후 독립 HTTP GET으로 PARTICIPATION REVOKED·WORK/AWARD VALID와 모든 claims=true, PDF/ZIP 6개 HTTP 200·파일 시그니처를 확인했다.
- 재시작 후 업무 readback: `13:07:45Z~13:07:51Z` **8 checks PASS**(학생 로그인 1회 + GET 7회). 동일 profile의 3학점(적용된 CSV 과목 1개), 증빙 VERIFIED·검수 2건·L2·파일 hash, 동일 과목 ID의 MAJOR_REQUIRED와 현재 기관 학사 단위, 공개 3종 상태와 기존 두 거래 digest 보존을 확인했다.
- Chrome 화면: PARTICIPATION reload 후 취소 상태 헤딩, AWARD lookup 후 유효 수상·상격 `대상`을 screenshot으로 확인했다. 전체 33경로 및 로그인 후 전체 브라우저 흐름 완주는 미수행이다.
- 당시 AWARD의 `Proof 직접 재계산`으로 연 live Tamper Lab은 원문 claims·leaf·ProofRoot 재계산 일치를 확인했다. 그러나 Kaia 전용 메타데이터 검사로 `Kaia 공개 기록 없음`·`증거 불일치 확인 필요` 경고와 `Chain 0`이 표시됐고 테스트 카드도 Java 629·프론트 19라는 옛 고정 문구였다. 이 v6 관찰을 Tamper Lab 전체 PASS나 프론트 Sui 완전 호환으로 기록하지 않는다. 당시 두 UI 오류 한정 범위에서는 유지했고 후속 전반 기능 보완에서 로컬 코드·회귀를 완료했다.
- 격리 infra 도구 fresh **19/19 PASS**, SQL guard 11개 검사 확인. 기존 운영 container ID 변경 없음·HTTP 200·Kaia 수상 6개 유지, 이전 격리 DB 30 tables/full DB 48 tables 분리 보존을 확인했다.

## 7. 중요 경계와 알려진 위험

1. 현재 Credential 타입은 공모전 `PARTICIPATION`, `WORK`, `AWARD`뿐이다.
2. 졸업요건은 공식 판정이 아니라 일부 정책을 다루는 자가점검 MVP다.
3. 외부 증빙은 L2 수동 2인 검수이며 발급기관 직접 검증이 아니다.
4. 공개 프로필은 UUID를 쓰고 개별 Credential의 공개 `subjectRef`도 증명별 별칭으로 투영한다. 원본 canonical·hash·signature는 유지한다. PUBLIC 이름·전공과 발급 당시 공개 요약은 남으며 현재 개인별 동의·철회와 완전한 익명성은 보장하지 않는다.
5. anchoring과 worker 기본값은 비활성이다. 과거 Kairos E2E가 현재 운영 활성화를 증명하지 않는다.
6. migration runner, 관측·alert, Mainnet key governance와 전체 앱 복구 검증은 남아 있다. 운영 온라인 백업을 별도 `network=none` MySQL에 실제 복원해 48 tables·AWARD 6·batch 1·tx 1·key 1을 확인했고 복원 container는 정지·volume 보존했다. 최종 write-paused 백업이나 RPO/RTO 입증과는 다르다.
7. reviewer endpoint는 `permitAll`이지만 capability token API다.
8. Swagger 보안표기는 공개 API의 `security=[]`, review body capability token, refresh 필수 cookie·logout 선택 cookie와 보호 API의 JWT를 구분하도록 로컬 회귀 완료했다. 실제 보안 정책은 변경하지 않았으며 권한 판단의 정본은 SecurityConfig와 서비스다.
9. 기존 운영에는 Kaia ANCHORED AWARD 6개, batch 1개, CONFIRMED tx 1개, key 1개가 있다. 현재 명시적 legacy Kairos allowlist의 혼합 READ_ONLY routing은 로컬 회귀 완료했으나 실제 운영 증명·RPC·코드 hash 검증, 기관의 새 Sui keyVersion/승인과 DB upgrade 게이트가 남는다. 쓰기는 active provider 하나뿐이며 구형 Kaia 증명의 취소를 Sui에 보내지 않는다.
10. 영속 E2E의 claim·DB·gateway journal·원문·서명을 삭제하거나 새 실행으로 덮어쓰지 않는다. 재개는 보존된 동일 상태를 대조하는 승인된 절차로만 진행한다.
11. Tamper Lab의 Sui/Kaia 좌표·체크포인트와 실패 표시 계약, 고정 테스트 수 제거는 후속 로컬 회귀 완료했다. 이전 원격 v6에서 관찰한 UI가 자동 갱신된 것은 아니며 새 JAR·프론트 반영 및 전체 브라우저 검증은 별도다.

운영 온라인 백업 위치는 EC2 내부 `/srv/trekkey-backups/pre-replacement-20260908-online`(root 0700)이다. 민감 설정을 포함할 수 있어 외부 전송·내용 출력을 금지한다. 백업/복원과 현재 서비스 유지 결정의 상세는 [전체 통합 기록](./sui-full-integration-2026-09-08.md)을 따른다.

## 8. 비밀·개인정보 취급

- `.env`, private key, JWT secret, DB password, refresh token, invitation/review token을 문서·로그·응답에 넣지 않는다.
- 애플리케이션 설정을 감사할 때는 property 이름과 기본 동작만 확인하고 실제 환경값을 출력하지 않는다.
- 운영 host·개인 원문·학번·이메일·자격번호를 문서 reader에 복사하지 않는다.
- Credential hash 입력, schema profile, Merkle tree version, EIP-712 domain은 배포 후 조용히 바꾸지 않는다.

## 9. 종료 보고 형식

- 변경한 정본 Markdown
- 재생성한 HTML reader
- route·API exact count와 기준 commit
- 통과한 test·build·browser check
- skip·미실행과 정확한 이유
- Notion 동기화 여부. 이 저장소 문서화만 한 경우 `비대상`으로 명시
- 서버를 시작했다면 종료 여부, 시작하지 않았다면 `미기동`
