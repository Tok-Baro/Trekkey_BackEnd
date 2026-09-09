# Trekkey 프로젝트 현황

최신 실행 상태는 [Sui 서비스 전환·백업 기록](./sui-service-release-2026-09-09.md)을 우선한다. Java 851개(822 PASS/29 SKIP), 프론트 72개 PASS. 새 전체 백업·복원본 migration PASS, 새 앱 업로드/운영 전환은 보안 검토의 목적지·파일별 승인 요구로 미실행이다.

- 상태 기준일: 2026-09-08 KST
- 프론트엔드: `main@946525e` + 전반 기능 보완 working tree. 디자인/CSS와 기존 dirty README 보존
- 백엔드 현재 HEAD·통합 기준: `0615984`, `origin/main`과 일치
- 실행 산출물: 위 HEAD에 Sui·통합 수정 및 기존 사용자 변경을 더한 working tree snapshot. HEAD만으로 산출물 동일성을 보증하지 않음
- 최신 변경·실행·백업 정본: [기능 완성·회귀 검증](./completion-2026-09-08.md). 아래 v6 표는 변경 전 실행 이력이다.

## 한 문장 상태

Sui 통합 이후 수동 제출·심사위원 수정·심사 링크 복구, 혼합 체인 읽기, Sui 공개 UI, 졸업 coverage, 공개 ZIP/PDF·식별자·Swagger 계약을 보완했다. 현재 로컬 검증은 **Java 123 suites·842개=813 PASS/29 SKIP/실패 0·build PASS**, **프론트 72/72·build PASS**이며 새 JAR은 원격 전송·반영하지 않았다. legacy 쓰기와 실제 기관의 새 Sui keyVersion/승인, 기존 운영 DB upgrade 검증은 별도이므로 운영 완전 교체·공식 졸업사정·전체 개인정보 동의 관리는 완료로 주장하지 않는다.

## 현재 릴리스 경계

| 영역 | 코드 상태 | 운영 판단 |
| --- | --- | --- |
| 인증·권한·관리자 초대 | 구현 | 실제 사용자 흐름과 API 연결 |
| 공모전·팀·제출·심사·수상 | 구현 | 관리자 수동 제출·심사위원 수정까지 API/화면 연결·회귀 |
| `PARTICIPATION`·`WORK`·`AWARD` Credential | 구현 | 공모전 확정 경로에 연결 |
| Merkle·EIP-712·Kaia | 구현·기존 운영 유지 | 운영 DB에서 ANCHORED AWARD 6개·batch 1개·CONFIRMED tx 1개·issuer key 1개 확인 |
| Sui Move·gateway·Java adapter | 구현·격리 검증 | 영속 MySQL→공개 testnet 3종 발급·PARTICIPATION 취소 재개 PASS. 재시작 후 공개 HTTP/업무 보존·Chrome 공개 결과도 확인; 운영 이관과 전체 브라우저 coverage는 별도 |
| 공개 검증·PDF·ZIP·활동 프로필 | 구현 | 공개 subjectRef는 증명별 별칭. 취소·미확정 PDF 명시, TAMPERED ZIP 500 방지. 현재 개인별 동의/철회는 별도 |
| 한성대 졸업 자가점검 | 제한 MVP | coverage·학적 누락을 표시하고 전체 충족 오표시 차단. 공식 정책 전수 구현 아님 |
| 외부 증빙 | 제한 MVP | L2 2인 수동검수. 기관 API·전자서명·OCR·malware scan 없음 |
| 졸업·교과목·자격증 Credential | 미구현 | 현재 schema profile에 없음 |
| Mainnet·KMS/HSM·기관 키 거버넌스 | 미구현 | 운영 전 필수 게이트 |

## 코드 surface 기준과 역사적 감사 수치

| 항목 | 값 | 산출 기준 |
| --- | ---: | --- |
| 프론트 명시적 route | 33 | `src/router.jsx` + `src/routeConfig.js` exact set |
| 백엔드 controller | 37 | `origin/main@0615984`의 `@RestController` |
| 백엔드 handler mapping | 109 | 현재 working tree의 controller mapping 전수. 기존 ref의 107개에 신규 수동 접수·judge PATCH 추가 |
| 백엔드 고유 method+path | 108 | 신규 수동 접수·judge PATCH 포함. 동일 POST path의 consumes overload 2개를 1 operation으로 합침 |
| 당시 프론트 Node 테스트 | 19 passed, 0 failed | 2026-08-30 `npm test` 재실행. 이후 v6은 29개, 현재 후속 로컬 결과는 72개 |
| 당시 Solidity test | 12 passed, 0 failed | 2026-08-30 `npm test` 재실행. 당시 Node 25.9.0 비지원 경고 |

## 2026-09-08 이전 v6 실행 결과와 범위

아래는 후속 기능 보완 전의 v6 역사 기록으로, 시간 정합성 수정 후 당시 최종 Java/v6·Sui 동일 상태 resume·재시작 후 독립 HTTP/업무 readback 및 Chrome 공개 화면 결과다. 현재 로컬 회귀 수치나 새 JAR 배포 증거가 아니며, 전체 33경로와 로그인 후 모든 브라우저 흐름을 완주한 결과도 아니다.

| 항목 | 마지막 확인 결과 | 정확한 범위 |
| --- | --- | --- |
| Java 21 전체 | **760개: 733 PASS / 27 SKIP / 실패 0** | 117 suites·22초·build PASS. 전용 MySQL 25개와 H2 Sui/영속 Sui opt-in 각 1개 skip |
| 별도 실제 MySQL | **9개 class·25/25 PASS** | 전용 disposable schema. 일반 Java run의 skip과 중복 합산하지 않음 |
| 격리 상시 HTTP | **27 checks PASS** | 합성 3개 계정 로그인·권한, profile, 졸업 평가 4회, native MySQL 조회, PDF 증빙·2인 승인·동일 관리자 409, 성적 CSV import·course PATCH 재조회 |
| 승인된 프론트 두 수정 | **29/29 PASS·production build PASS** | 비동기 증빙 form reset, `maxTeamMembers` 반영. JSX handler/hook 회귀 10개 포함; 디자인/CSS 무변경, 브라우저 QA와 다름 |
| 영속 MySQL→실제 Sui | **동일 상태 resume 1/1 PASS·실패 0·skip 0** | 기존 두 거래 digest·bytes와 canonical 원문 불변. 최종 4 CONFIRMED·3 batches·1 status event·4 PROCESSED. PARTICIPATION REVOKED·WORK/AWARD VALID와 runner PDF/ZIP 검사 통과 |
| v6 격리 서버 적용 | **업로드 전후 해시 일치·18080 적용 완료** | 기존 운영 서비스 교체 아님 |
| 재시작 후 독립 공개 HTTP | **PASS** | 13:02:08Z 재시작 이후 3종 상태·모든 claims=true, PDF/ZIP 6개 HTTP 200·파일 시그니처 확인 |
| 재시작 후 업무 readback | **8 checks PASS** | 13:07:45Z~13:07:51Z, 학생 로그인 1회+GET 7회. profile 3학점·CSV 과목 1개, 증빙 VERIFIED/2 reviews/L2/file hash, 과목 ID/MAJOR_REQUIRED/현재 기관 unit, 공개 3종 상태·기존 두 digest 보존 |
| Chrome 공개 화면 | **관찰 완료** | PARTICIPATION reload 시 취소 상태, AWARD lookup 시 유효 수상·상격 `대상` screenshot 확인. 33경로·로그인 후 전체 브라우저 완주는 미수행 |
| Chrome live Tamper Lab | **재계산 일치·UI 호환 미완료** | AWARD의 `Proof 직접 재계산` 진입 후 원문 claims·leaf·ProofRoot 일치. Kaia 메타데이터 검사로 `Kaia 공개 기록 없음`·`증거 불일치 확인 필요` 경고와 `Chain 0` 표시. Java 629·프론트 19 테스트 카드는 옛 고정 문구이며 전체 PASS가 아님 |
| 격리 infra·SQL guard | **fresh 19/19 PASS·SQL guard 11개 검사 확인** | 도구 회귀 결과이며 운영 이관 승인으로 확대하지 않음 |
| 운영 온라인 백업 실제 DB 복원 | **PASS** | 별도 `network=none` MySQL에 48 tables·AWARD 6·batch 1·tx 1·key 1 복원 대조. 복원 container 정지·volume 보존 |

첫 영속 Sui 실행은 두 anchor CONFIRMED 후 WORK timestamp 반올림 차이로 TAMPERED 오탐이 발생해 실패했다. 이 이력은 보존하며 최종 resume PASS로 덮어 숨기지 않는다. 수정·재개는 기존 두 거래와 원문을 변경하지 않고 남은 AWARD anchor·REVOKE를 완료했다.

- 신규 AWARD: `5KDLVPwr6W9upTBAWdBsLeoeLcDX2rWTGqYme53jJ3Qh`, checkpoint `381280961`.
- 신규 REVOKE: `7aKBJK8hABvhVoLJFtb4AsP4bR9ByAu83qcQ8hgJUESP`, checkpoint `381281042`.

격리 runtime은 JWT와 다른 증빙 HMAC secret을 주입하고 TTL을 초 단위(access 900, refresh 604800)로 수정했다. 이는 운영 배포 전체의 설정 강제나 공식 졸업 판정의 정확성을 보증하지 않는다. 이번 재시작·공개 화면/데이터 보존 확인과 달리 전체 로그인 브라우저 흐름·모든 경로·전체 앱 재해 복구는 별도 검증이 필요하다.

## 테스트 증거를 읽는 법

| 시점·출처 | 결과 | 사용할 수 있는 주장 | 사용할 수 없는 주장 |
| --- | --- | --- | --- |
| 2026-07-21 `docs/API_TEST_REPORT.md` | HTTP 28개 최종 통과, 단위·controller 109개 통과 | 당시 인증·권한·기본 대회 시나리오 회귀 | 최신 Credential·심사·졸업·증빙 전체 회귀 |
| 2026-08-18 증빙 QA 문서 | 백엔드 664 실행, 외부 MySQL 25 + 실제파일 2 조건부 skip, 실패 0 | 해당 통합 시점의 증빙·졸업 구현 검증 | 이후 `origin/main` 전체 최신 green 상태 |
| 2026-08-24 로컬 test report artifact | 629 tests, 0 failures, 25 ignored | artifact 생성 시점의 역사적 결과 | artifact가 어느 commit에서 생성됐는지 없는 최신 CI 증거 |
| 2026-08-30 프론트 재실행 | 19 passed, 0 failed | 당시 프론트 Node 단위 테스트 | React route·component 통합·브라우저 E2E |
| 2026-08-30 프론트 production build | Vite build 성공, 1,795 modules transformed | 당시 source의 정적 production bundle 생성 | 배포·실사용 API 연결 성공 |
| 2026-08-30 Solidity 재실행 | 12 passed, typecheck 성공 | 당시 contract test와 TypeScript 정합성 | 지원 Node LTS·Kairos·Mainnet 실거래 성공 |

2026-08-30 감사 당시에는 host의 Java runtime 부재로 백엔드 재실행을 못 했다. 이후 Java 21로 v6의 760개 run과 현재 후속 로컬 842개 run을 완료해 이 과거 제한은 해소됐다. v6의 27개 skip과 현재 29개 skip은 각 실행에 속하며, 별도 MySQL/Sui 성공을 현재 일반 run의 통과나 모든 브라우저 흐름·운영 이관 완료로 확대하지 않는다.

역사적 contract test에는 Node `25.9.0` 비지원 경고가 남아 있다. 지원 LTS runtime의 재현 증거와 실제 네트워크 실행 결과는 각각 구분한다.

## 공개·개인정보 경계

### 현재 보장 가능한 범위

- 증빙 원문과 canonical Credential 원문은 블록체인에 저장하지 않는다.
- 온체인에는 Merkle Root, 발급자·schema·batch 메타데이터와 상태를 기록한다.
- 공개 활동 프로필은 UUID 공개 ID와 공개 요약을 사용하며 on/off와 link rotation을 지원한다.
- 개별 Credential verification/package의 공개 `subjectRef`는 증명별 별칭이며 내부 숫자 사용자 PK를 반환하지 않는다. 원본 canonical·hash·signature는 변경하지 않는다.
- 증빙 자격번호 원문은 HMAC과 마지막 네 자리로 대체한다.

### 아직 보장할 수 없는 범위

- PUBLIC 이름·전공과 발급 당시 공개 요약은 남는다. 공개 subject 별칭은 현재 개인별 공유 동의·철회 검증이나 증명 간 공통 불변 식별자를 뜻하지 않는다.
- 활동·필드 단위 selective disclosure는 구현 전이다.
- 알려진 개별 Credential 공개 URL을 사용자가 직접 폐기·교체하는 기능은 없다.
- 증빙 파일 malware scan, OCR, object storage와 retention cleanup은 구현 전이다.

## 운영·배포 상태

현재 운영 기준은 `0615984`이며 기존 Kaia 서비스·DB를 유지한다. v6 최종 확인에서도 운영 container ID 변경 없이 HTTP 200·Kaia 수상 6개가 유지됐고, 이전 격리 DB 30 tables와 full DB 48 tables는 분리 보존됐다. 당시에는 기존 AWARD 6개의 읽기 경로가 없어 단일 Sui 교체 조건을 충족하지 않았다. 현재 혼합 체인 READ_ONLY routing은 로컬 구현·회귀 완료했으나 후속 새 JAR 원격 업로드는 실행 전 보안 검토에서 중단돼 전송·반영하지 않았다. 실제 legacy 검증·기관 키·기존 DB upgrade 등 이관 게이트는 여전히 남는다.

EC2 내부 `/srv/trekkey-backups/pre-replacement-20260908-online`에 root 0700 온라인 snapshot을 보존했다. 별도 네트워크 차단 MySQL의 실제 복원·48 tables/핵심 원장 건수 대조는 성공했고 복원 container는 정지, volume은 보존했다. 온라인 snapshot은 최종 write-paused 일관성 백업이나 전체 애플리케이션 복구·RPO/RTO 검증을 대신하지 않는다. 상세는 [전체 통합 기록](./sui-full-integration-2026-09-08.md)을 따른다.

코드에는 컨테이너 build, non-root 실행, loopback bind, healthcheck와 이미지 rollback 흐름이 있다. 아직 별도 게이트가 남은 부분은 다음과 같다.

1. 운영 schema의 모든 migration 적용/rollback과 기존·신규 공개 검증 URL 호환
2. anchoring mode와 worker 실제 활성화 여부
3. relayer·issuer key의 KMS/HSM·multisig 이관 상태
4. Actuator, metric, alert, outbox backlog·RPC readiness
5. write-paused DB·파일·journal 일관성 백업, 전체 앱 복구와 RPO/RTO
6. HTTPS 설정이 CD에서 자동 보장되는지 여부
7. 격리 runtime에서 확인한 HMAC 분리를 운영 구성에도 강제하는 절차 — 코드의 JWT fallback은 남음

SQL migration 파일은 존재하지만 Flyway/Liquibase나 자동 배포 migration 단계는 없다. 격리 full runtime은 명시적 빈 schema bootstrap 후 `ddl-auto=validate`를 사용하지만 기존 설정의 `update` 의존과 실제 데이터 upgrade/rollback 검증은 별도 운영 gate다.

## 우선순위 게이트

### P0 — 공개·정합성

1. 공개 subjectRef 내부 PK 노출은 응답 별칭으로 수정했으나, PUBLIC 기본 지정과 현재 개인별 공유 동의/철회 정책은 별도 설계 필요
2. 혼합 체인 읽기·Sui UI는 코드/회귀 완료. 새 JAR 전송 승인, 실제 운영 legacy 검증/DB upgrade·기관 키 이관·legacy 쓰기와 전체 브라우저 coverage 확보
3. DB migration runner 도입, 현재 schema drift와 rollback 검증
4. 졸업 coverage와 부분 충족 오표시는 수정. 검증된 학적 정보와 전체 공식 정책이 없으므로 항목별 점검만 제공
5. 격리 환경에서 완료한 HMAC/JWT 분리를 운영 구성에도 필수화

### P1 — 운영성

1. blockchain worker·RPC·outbox backlog readiness와 alert
2. issuer key를 외부 wallet·KMS/HSM으로 분리하고 권한 이관 rehearsal
3. object storage, malware scan, OCR와 증빙 retention
4. 성공한 DB 복원 시험을 넘어 파일·journal·proof package 포함 앱 복구와 보존 정책 검증
5. Swagger 보안표기는 회귀 완료. operation summary·tag와 internal surface 분리는 별도

### P2 — 제품 확장

1. 전체 학과·트랙·입학연도 공식 졸업 정책
2. 졸업·교과목·자격증 Credential adapter
3. 발급기관 API·전자서명·issuer registry
4. 활동·필드 단위 선택 공개와 기업용 검증 대시보드
5. Mainnet 전환 기준과 비용·처리시간·복구시간 측정

## 문서 드리프트

- 2026-08-30의 `e113959`/16커밋 지연 설명은 역사 기록이다. 현재 `0615984` working tree의 Sui·통합·사용자 변경을 보존한다.
- Sui 최초 이식, 이전 v6 영속 MySQL/HTTP 기록과 후속 로컬 회귀를 혼합하지 않는다. 최신 변경·실행 상태는 [기능 완성·회귀 검증](./completion-2026-09-08.md), 이전 v6 원격 실행 이력은 [전체 통합 기록](./sui-full-integration-2026-09-08.md)을 따른다.
- 상세 졸업·증빙 API 설계에는 목표 endpoint와 구현 endpoint가 섞여 있다. 구현 정본은 [API 카탈로그](./spec/api-catalog.md)다.
- 과거 blockchain 설계·runbook의 “업무 연결 전” 표현은 현재 코드와 다를 수 있다. 이 문서와 [구현 기능 카탈로그](./implemented-features.md)의 기준일을 우선 확인한다.
- 공학경진대회 보고서의 “내부 사용자 ID 미노출”은 현재 공개 응답의 별칭 투영에 한정하며 원본 canonical 변경이나 완전한 익명성으로 확대하지 않는다. 최신 테스트 수는 Java 842개·프론트 72개와 skip·로컬 실행 범위를 함께 표시한다.

## 다음 상태 갱신 조건

다음 중 하나가 바뀌면 이 문서와 기능 카탈로그를 함께 갱신한다.

- 프론트 route 또는 API 소비 변경
- controller method+path 변경
- Credential schema·hash·Merkle·EIP-712 계약 변경
- 졸업 정책 coverage나 공식 학사 연동 변경
- 증빙 assurance·기관 검증·만료·철회 처리 변경
- 배포·migration·backup·key management gate 변경
- 새로운 commit-bound 테스트 또는 운영 측정 결과 생성
