# Trekkey 문서 허브

최신 배포 준비 상태: [Sui 서비스 전환·백업 기록](./sui-service-release-2026-09-09.md). 전체 백업과 복원본 schema 검증은 완료했고, 구체적인 산출물 전송 승인을 기다려 실제 운영 교체는 하지 않았다.

현재 기능 보완·검증 정본: [기능 완성·회귀 검증](./completion-2026-09-08.md). 아래 v6 수치는 이전 실행이며 당시 결과를 보존한다.

2026-09-08: 현재 `HEAD=0615984`에 최신 업무 기능과 Sui·통합 수정 및 기존 사용자 변경을 보존한 working tree다. 최신 정본인 [전체 통합 검증·운영 인계](./sui-full-integration-2026-09-08.md)를 먼저 읽는다. 프론트는 별도로 승인된 비동기 증빙 form reset·대회별 팀 정원 두 오류만 수정했으며 디자인/CSS는 그대로다. 기존 운영 Kaia 증명 6개 때문에 단일 Sui 대체 조건은 충족하지 않아 운영 서비스·DB를 유지한다.

최종 Java 결과는 **760개: 733 PASS / 27 SKIP / 실패 0, 117 suites·22초·build PASS**다. 별도 MySQL **25/25 PASS**, 상시 HTTP **27 checks PASS**, 프론트 **29/29·build PASS**, 영속 Sui 동일 상태 재개 **1/1 PASS**를 확인했다. 첫 실행의 WORK timestamp 반올림 오탐을 수정했고 기존 두 거래·canonical 원문을 보존한 채 최종 **4 CONFIRMED·3 batches·1 status event·4 PROCESSED**, PARTICIPATION REVOKED·WORK/AWARD VALID와 runner PDF/ZIP 검사를 통과했다.

v6는 업로드 전후 해시 일치 후 격리 `18080`에 적용·재시작했다. 이후 독립 HTTP에서 3종 상태·claims와 PDF/ZIP 6개를 확인했고 별도 업무 readback **8 checks PASS**, Chrome의 PARTICIPATION 취소·AWARD 유효 수상/`대상` 화면을 확인했다. fresh infra **19/19 PASS**와 SQL guard 11개 검사도 확인했다. 전체 33경로 및 로그인 후 전체 브라우저 흐름 완주는 미수행이다.

AWARD의 `Proof 직접 재계산`으로 연 live Tamper Lab은 원문 claims·leaf·ProofRoot가 일치했으나 기존 Kaia 메타데이터 검사로 `Kaia 공개 기록 없음`·`증거 불일치 확인 필요`와 `Chain 0`을 표시했다. Java 629·프론트 19 테스트 카드도 옛 고정 문구다. 일반 공개 검증 성공을 Tamper Lab 전체 PASS나 프론트 Sui 완전 호환으로 일반화하지 않는다. 이 미수정 UI와 기존 Kaia 증명 6개의 호환 문제가 단일 Sui 완전 대체의 남은 조건이다.

기존 운영 container ID는 바뀌지 않았고 HTTP 200·Kaia 수상 6개를 유지한다. 이전 격리 DB 30 tables/full DB 48 tables도 분리해 보존한다. 운영 온라인 백업의 별도 `network=none` MySQL 복원은 48 tables와 핵심 원장 건수 대조에 성공했지만 전체 앱 복구·단일 Sui 운영 전환 성공을 뜻하지 않는다.

Markdown가 정본이고 [검색 가능한 HTML reader](./documentation-home.html)는 생성물이다. 처음 들어온 사람과 에이전트는 아래 순서로 읽는다.

## 1. 현재 상태 정본

최신 실행 수치·백업·운영 교체 판단: [전체 통합 검증·운영 인계](./sui-full-integration-2026-09-08.md). [최초 이식 기록](./sui-migration-2026-09-08.md)과 [이전 격리 테스트넷 기록](./sui-testnet-server-runbook.md)은 해당 시점의 근거로 읽는다.

1. [구현 기능 카탈로그](./implemented-features.md)
   - 실제 코드에 근거한 `운영 화면`·`연구 화면`·`제한 운영`·`내부 운영`·`구현 전` 분류
   - 과장하면 안 되는 졸업·증빙·개인정보·블록체인 경계
2. [프로젝트 현황](./project-status.md)
   - 기준 commit, 테스트 증거, 운영 미확인 항목과 P0~P2 gate
3. [화면·라우트 명세](./spec/pages.md)
   - 프론트 33개 route exact set, 접근 역할, API 소비와 UI 제한
4. [HTTP API 카탈로그](./spec/api-catalog.md)
   - 최신 통합 백엔드의 37 controllers, 107 handlers, 106 unique operations
5. [에이전트 인수인계](./agent-handoff.md)
   - 코드 정본, 변경 계약, 감사·테스트 명령과 안전 경계
6. [문서 도구](./tools/README.md)
   - HTML reader 생성, route/API parity와 link 검증

## 2. 구조·Credential 정본

- [업무·블록체인 전체 통합 ERD](./unified-erd.md) · [확대 SVG](./assets/trekkey-unified-erd.svg) · [Raw Mermaid](./trekkey-unified-erd.mmd)
- [공모전·Credential 최종 ERD](./erd.md)
- [MVP ERD 설명](./erd-mvp.md)
- [공모전 schema](./contest-domain-schema.md)
- [심사 도메인 최종 ERD 정렬](./review-domain-final-erd-alignment.md)
- [팀 회의용 아키텍처 다이어그램](./architecture-diagrams.md)
- [최신 전체 통합·영속 MySQL·HTTP·Sui 검증](./sui-full-integration-2026-09-08.md)
- [Sui 이식 구현·검증·운영 전 gate](./sui-migration-2026-09-08.md)
- [Credential·Kaia 앵커링 설계](./blockchain-anchoring-architecture.md)
- [블록체인 구현·Kairos runbook](./blockchain-implementation-runbook.md)
- [공개 활동 프로필](./public-activity-profile.md)
- [관리자 인증·권한·감사](./ADMIN_SECURITY.md)

설계 문서의 과거 상태 표현이 현재 기능 카탈로그와 충돌하면 [구현 기능 카탈로그](./implemented-features.md)와 [프로젝트 현황](./project-status.md)의 기준일·commit을 우선한다. hash 입력, schema profile, Merkle tree version과 EIP-712 domain은 배포 후 조용히 변경하면 안 된다.

### 졸업요건·외부 증빙 문서

1. [SW·AI중심대학 졸업요건 검사 설계](./graduation-requirement-design.md)
   - 2025년 SW중심대학 58개교 대상 레지스트리
   - 학교·학번·전공·입학유형별 버전형 졸업정책
   - 성적표·비교과 증빙과 `SATISFIED/UNSATISFIED/UNKNOWN` 판정
   - 한성대학교 공통 졸업요건 파일럿 규칙

2. 한성대학교 졸업요건 MVP 문서 세트
   - [기능 설계서](./hansung-graduation-requirement-design.md)
   - [ERD 및 테이블 명세](./hansung-graduation-erd.md)
   - [API 및 프론트 계약](./hansung-graduation-api-spec.md)
   - [3-cycle QA 보고서](./hansung-graduation-qa-report.md)
   - 한성대 공식 학사기준과 현재 백엔드·프론트 구조를 연결한 구현 기준

3. 외부 증빙 검증 문서 세트
   - [신뢰 모델 및 처리 설계](./external-evidence-verification-design.md)
   - [ERD 및 테이블 명세](./external-evidence-verification-erd.md)
   - [API 및 프론트 계약](./external-evidence-verification-api-spec.md)
   - [3-cycle 설계 QA](./external-evidence-verification-qa-report.md)
   - 현재 구현은 2인 수동검수 L2이며 기관 API·전자서명 검증은 설계상의 후속 단계다.

## 3. 출품·발표

- [2026 공학경진대회 기획](./engineering-competition-2026-plan.md)
- [2026 공학경진대회 1차 예비 심사 보고서](./engineering-competition-preliminary-report-2026.md)
- `Trekkey_공학경진대회_1차_예비심사_보고서.docx`

출품 문구와 테스트 숫자는 역사적 결과를 최신 결과처럼 사용하지 않는다. 현재 개별 Credential의 내부 숫자 subject 참조, 졸업 자가점검 정책 coverage와 외부 증빙의 L2 수동검수 한계를 반드시 함께 표시한다.

## 4. 역사·운영 참고

- [2026-07-21 API 실기동 테스트 보고서](./API_TEST_REPORT.md)
- [2026-07-27 통합 회의 안건](./meetings/2026-07-27-integration-agenda.md)
- `docs/migrations/*.sql`

날짜가 있는 보고서와 회의록은 당시의 증거다. 현재 상태를 결정할 때는 코드와 fresh test를 다시 확인한다.

## 현재 구현 요약

공모전 운영과 `PARTICIPATION`·`WORK`·`AWARD` Credential 발급, Merkle·Kaia 및 Sui 전용 승인/adapter, 공개 검증과 학생 공개 활동 프로필이 구현돼 있다. 공개 Sui testnet 배포·이전 H2 발급/취소에 이어 최신 영속 MySQL 3종 발급·PARTICIPATION 취소, 재시작 후 공개 HTTP·업무 데이터 보존 및 Chrome 공개 화면 확인까지 완료했다. 졸업요건은 일부 한성대 정책의 비공식 자가점검 MVP이며, 외부 증빙은 서로 다른 관리자 2인의 L2 수동검수 MVP다. 졸업·교과목·자격증 Credential, 발급기관 직접 검증, 활동·필드 단위 선택 공개, opaque subject ID, 과거 Kaia와 Sui의 자동 라우팅 및 Mainnet 운영은 후속 gate다.
