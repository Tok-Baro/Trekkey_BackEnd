# 외부 증빙 검증 설계 3-cycle QA 보고서

## 1. 범위와 결과

검증 대상은 신뢰 모델, ERD, API/프론트 계약과 기존 졸업요건·Credential 구조의 연결이다. 2026-08-18 수동검증 MVP 구현 후 코드·HTTP 계약·회귀 테스트를 같은 세 관점으로 다시 수행했다. 실제 외부 기관 응답과 부하 수치는 아직 검증 대상이 아니다.

### 구현 QA 결과

| cycle | 실행 | 결과 |
| --- | --- | --- |
| 1 | 파일 magic byte/크기, 2인 합의, 동일 관리자 재검수 단위 테스트 | 통과 |
| 2 | assurance 최소값 및 `OTHER` 안의 자격증/공모전 유형 오인정 회귀 테스트 | 통과 |
| 3 | 실제 multipart MockMvc, 참가자/관리자 역할 분리, 전체 Gradle 회귀 테스트 | 통과 |

- 대상 테스트: evidence 도메인 + `GraduationEvaluationServiceImplTest`
- 전체 회귀: duplicate `* 2.java`를 source set에서 제외하고 651개 실행, 25개 외부 MySQL 조건부 skip, 실패 0
- 전체 테스트에는 기존 프로젝트가 요구하는 JWT expiration, front URL, CORS, upload directory 환경값을 명시했다.
- 프론트는 학생 제출·상태 화면과 관리자 1·2차 검수 화면을 추가하고 `npm test`, `npm run build`를 통과했다.

| cycle | 관점 | 발견 | 조치 | 결과 |
| --- | --- | --- | --- | --- |
| 1 | 기존 코드·데이터 정합성 | 기존 `VerificationStatus`에는 단순 `VERIFIED`가 없고 평가기가 `DOCUMENT_VERIFIED`, `UNIVERSITY_VERIFIED`를 동일하게 취급 | 외부 decision은 `DOCUMENT_VERIFIED`로 매핑하고 assurance 컬럼과 정책 최소값을 추가 | 통과 |
| 2 | 공격·권한 우회 | 업로드, QR/URL, 이메일, webhook과 수동검수 각각에 위조·SSRF·재전송·자기승인 위험 | MIME/magic/AV, host/IP 제한, nonce·서명·idempotency, 2인 분리검수와 조직 범위 규칙 명시 | 통과 |
| 3 | 장애·수명주기 | 기관 장애를 허위로 오판하거나 만료/철회 뒤 졸업요건이 계속 충족될 위험 | `INCONCLUSIVE` 분리, retry/outbox, 불변 decision, binding 해제와 재평가 event 명시 | 통과 |

## 2. Cycle 1 — 스키마·도메인 정합성

### 확인 항목

- 기존 `StudentNonCourseRecord`와 `GraduationEvaluationServiceImpl`의 검증 enum
- 기존 `AncCredentialSource`의 source type별 정확히 하나인 FK 규칙
- public ID, organization 범위, 불변 정책 version 등 기존 프로젝트 규칙
- 제출 파일 테이블과 외부 증빙 파일의 책임 중복

### 발견 및 결정

1. 기존 비교과 검증 enum은 `SELF_REPORTED`, `DOCUMENT_VERIFIED`, `UNIVERSITY_VERIFIED`, `REJECTED`다. 새 decision의 `VERIFIED`를 그대로 저장할 수 없으므로 외부 증빙 성공은 `DOCUMENT_VERIFIED`, 학교 원장 직접 확인만 `UNIVERSITY_VERIFIED`로 매핑한다.
2. 기존 평가기는 두 verified 상태를 모두 참으로 본다. 검증 강도를 적용하려면 `verification_assurance_level`과 정책 JSON의 `minimumAssuranceLevel` 비교가 추가되어야 한다.
3. 기존 credential source는 TEAM/SUBMISSION/AWARD 중 FK 하나만 허용한다. `EXTERNAL_EVIDENCE`를 단순 enum에만 추가하면 switch와 DB 제약이 깨지므로 `external_evidence_decision_id`, index, FK, 정확히 하나 CHECK를 함께 migration한다.
4. 공모전 제출 파일과 개인정보 증빙은 보존기간·접근권한이 달라 같은 `SUBMISSION_FILE`을 재사용하지 않고 별도 `EVIDENCE_FILE`로 둔다. storage port 구현은 재사용 가능하다.

### Cycle 1 판정

문서에 위 변경점을 반영했다. 현재 코드에 테이블만 먼저 추가하거나 enum만 먼저 추가해서는 안 되며 migration, entity, service, evaluator를 한 배포 단위로 묶어야 한다.

## 3. Cycle 2 — 보안·오남용

### 공격 시나리오와 방어

| 시나리오 | 기대 결과 |
| --- | --- |
| PDF 확장자로 위장한 실행 파일 | magic byte/MIME/AV 단계에서 차단, case 미생성 |
| QR에 `localhost`, private IP, cloud metadata URL 삽입 | 서버 URL policy에서 차단, 브라우저 직접 fetch 금지 |
| 공식 host에서 private IP로 DNS rebinding | resolve 전후 IP 확인과 private/link-local 대역 차단 |
| 공식 페이지와 닮은 피싱 도메인 | provider의 정확한 host allowlist 불일치로 차단 |
| 기관 확인 메일을 개인 메일로 발송 | issuer registry의 domain/address 정책 불일치로 차단 |
| challenge 링크 재사용 | nonce hash, 만료, `consumed_at`으로 두 번째 요청 거절 |
| webhook 재전송·서명 위조 | signature/timestamp/idempotency 검증, decision 중복 없음 |
| 제출자 본인 또는 같은 관리자의 2회 승인 | reviewer separation 규칙으로 409 |
| 다른 학교 관리자의 public ID 추측 | `(organization_id, public_id)` 조회로 404/403 |
| 동일 상장번호를 여러 계정에 재사용 | 자동확정 중단, 중복 risk queue; 팀 수상은 명시적 subject binding 검사 |
| 로그/체인에서 개인정보 유출 | 원문 미기록, response hash와 비식별 bundle hash만 기록 |

### Cycle 2 판정

OCR 점수나 이미지 유사도만으로 확정되는 경로가 없고, 모든 확정 경로에 issuer와 subject 검사가 있다. SSRF, replay, IDOR, 자기승인에 대한 설계 통제가 명시되어 통과다.

## 4. Cycle 3 — 장애·재검증·운영

### 시나리오와 기대 상태

| 상황 | 기대 상태/동작 |
| --- | --- |
| provider timeout, 429, 5xx | backoff 재시도 후 `INCONCLUSIVE`; `REJECTED` 금지 |
| worker가 callback 처리 중 재시작 | outbox/idempotency로 재처리, attempt/decision 한 건 |
| 발급기관이 나중에 자격 취소 | 새 `REVOKED` decision, binding 해제, 졸업요건 재평가 |
| 유효기간 도래 | 만료 scan이 `EXPIRED` decision 생성 및 재평가 |
| 사용자가 오입력 정정 | 기존 row update 대신 superseding submission |
| 두 수동 검수자 의견 불일치 | 자동확정 금지, 상위 queue 전환 |
| provider 설정/키 변경 | config version과 당시 attempt hash 보존, 기존 decision 재현 가능 |
| 원본 보존기간 종료 | object 삭제와 `deleted_at` 기록, decision/bundle hash 감사이력 보존 |
| 재평가 도중 장애 | decision/binding/outbox transaction으로 유실 없이 재시도 |

### Cycle 3 판정

외부 장애와 허위 제출이 분리되고, 성공 후 만료·취소까지 졸업요건에 전파된다. 운영 queue와 감사 자료가 남아 장애 복구 가능하므로 통과다.

## 5. 구현 착수 전 필수 결정

- 한성대에서 `L2`를 인정할 증빙 유형과 반드시 `L3` 이상이어야 할 유형
- 학교 검수자 역할 및 2인 승인 대상
- 유형별 원본 보존기간과 개인정보 처리 동의 문구
- 첫 provider(Q-Net 수동확인, 특정 공모전 기관 challenge 등)의 실제 약관·권한
- object storage, malware scanner, OCR, secret manager 운영 제품

이 결정이 없으면 공통 플랫폼과 mock provider까지는 구현할 수 있지만 실제 자료를 졸업 충족으로 확정하는 운영은 시작하면 안 된다.
