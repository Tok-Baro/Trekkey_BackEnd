# 한성대학교 졸업요건 설계 QA 보고서

## 1. 검증 범위와 결론

- 검증일: 2026-08-11
- 대상: 기능 설계서, ERD·테이블 명세, API·프론트 계약
- 전제: 한성대학교 학사시스템에서 학생 학적·성적을 제공하는 외부 API 없음
- 비교 대상: 현재 Spring Boot 3.5 / Java 21 / JPA / MySQL 8 구조, `USER`, `ORGANIZATION`, `ADMIN_AUDIT_LOG`, `SecurityConfig`

세 번의 독립 관점 검증을 수행했다. 최초안 그대로는 데이터 이중 원천과 불완전 입력의 오판 가능성이 있어 구현 승인 불가였고, 아래 수정 후에는 **조건부 구현 가능**으로 판정한다. P0 항목을 migration과 테스트로 먼저 해결해야 운영 반영할 수 있다.

## 2. Cycle 1 — 관계형 스키마·기존 코드 호환성

### 확인 방법

1. 신규 12개 테이블의 PK, FK, unique, nullability, 삭제 방향 검토
2. 현재 `Organization`, `User`, `BaseEntity`, `AdminAuditLog` entity와 대조
3. MySQL 8 및 `spring.jpa.hibernate.ddl-auto=update` 환경에서 실제 생성 가능성 검토
4. 다중 조직에서 교차 참조가 생기는 경로 역추적

### 발견 및 조치

| 심각도 | 발견 | 위험 | 반영 조치 | 상태 |
| --- | --- | --- | --- | --- |
| P0 | 한성대를 organization 이름, PK 또는 환경별 UUID로 식별할 가능성 | 이름 변경·seed 순서에 따라 다른 학교에 정책 적용 | `ORGANIZATION.code` unique/not-null 추가, `HANSUNG_UNIVERSITY` 사용 | 설계 반영 |
| P0 | profile에 organization과 student number를 USER와 중복 저장 | USER 변경 시 서로 다른 학생·학교 값 존재 | 중복 컬럼 제거, USER에서 조회 | 설계 반영 |
| P0 | `ddl-auto=update`가 FK 삭제 정책과 조건부 unique를 보장한다고 가정 | 운영 DB에 느슨하거나 다른 제약 생성 | 명시적 MySQL migration 필수화 | 설계 반영 |
| P1 | `ACADEMIC_COURSE`에 외부 식별자 없음 | 내부 PK를 API에 노출하거나 안전한 수정 불가 | `public_id` UUID unique 추가 | 설계 반영 |
| P1 | 평가-정책 연결 테이블만 BaseEntity 원칙과 불일치 | JPA composite key 복잡도와 감사시각 누락 | surrogate `id`와 unique 조합 사용 | 설계 반영 |
| P1 | 작성자 없이 작성자/검수자 분리 요구 | 정책 발행 통제 구현 불가 | `GRADUATION_POLICY.created_by` 추가 | 설계 반영 |
| P1 | FK의 삭제 동작 미정 | 정책 삭제로 과거 결과 손상 또는 cascade 폭발 | 발행 정책·평가 이력 `RESTRICT`, 프로필은 service 삭제 | 설계 반영 |
| P2 | 수강 중 과목도 grade not-null | `IN_PROGRESS` 저장 불가 | grade nullable, 상태별 validation | 설계 반영 |

### Cycle 1 판정

ERD 논리 구조는 수정 후 사용 가능하다. 단, entity부터 만들고 Hibernate가 테이블을 갱신하게 하는 방식은 승인하지 않는다. 먼저 migration을 작성하고 CI의 MySQL 서비스에서 실제 제약을 검증해야 한다.

## 3. Cycle 2 — 졸업요건 판정 정확성·데이터 완전성

### 확인 방법

1. 학교 API 없이 수기·CSV만 존재하는 경우의 참/거짓/미상 3값 논리 검토
2. profile 누계와 과목 상세의 각 규칙별 데이터 원천 추적
3. 재수강, F 이력, 편입 인정학점, 다중 트랙, 수동 졸업작품 시나리오 검토
4. 과거 평가 조회가 실제로 재현 가능한지 snapshot 필드 검토

### 발견 및 조치

| 심각도 | 발견 | 위험 | 반영 조치 | 상태 |
| --- | --- | --- | --- | --- |
| P0 | 입력하지 않은 과목을 없는 과목으로 해석 | 부분 입력 학생을 `UNSATISFIED`로 오판 | `record_completeness` 추가, 불완전하면 `UNKNOWN` | 설계 반영 |
| P0 | profile 총학점과 상세 과목 합계가 모두 진실 원천 | 같은 학생에게 상반된 판정 가능 | summary는 총학점 기준, detail은 카테고리 기준; 불일치 시 ELIGIBLE 차단 | 설계 반영 |
| P0 | hash만 저장해 과거 입력을 복원할 수 없음 | 정책·입력 변경 후 결과 설명 불가 | 평가에 정규화 입력 JSON과 profile version 저장 | 설계 반영 |
| P1 | 과거 item에 규칙 파라미터 snapshot 없음 | 기준 변경 시 당시 부족값 검증 불가 | rule type, parameters, required snapshot 추가 | 설계 반영 |
| P1 | summary만 입력해도 교양·트랙 판정 가능해 보임 | 근거 없는 자동 충족 | `input_mode=SUMMARY_ONLY`면 과목 기반 규칙 UNKNOWN | 설계 반영 |
| P1 | 조기졸업의 과거 F 이력을 현재 과목만으로 추정 | 재수강 후 F가 사라진 경우 오판 | `fail_history_status=UNKNOWN/NONE/EXISTS` 추가 | 설계 반영 |
| P1 | 학생 자가신고 증빙이 학교 확인처럼 보일 가능성 | 작품·논문 요건 거짓 충족 | `SELF_REPORTED`는 항상 UNKNOWN, 관리자만 verified | 기존/재확인 |
| P2 | 누계 기준 학기가 없음 | 서로 다른 시점의 summary/detail 비교 | `summary_as_of_term` 추가 | 설계 반영 |
| P1 | 편입생 총학점과 본교 과목 합계를 직접 비교 | 전적대 인정학점만큼 항상 불일치 | 본교학점↔본교 과목, 총학점↔본교+인정학점으로 검산 분리 | 설계 반영 |

### 판정 truth table

| 데이터 존재 | 완전성 | 검증 필요 | 값 비교 결과 | 규칙 상태 |
| --- | --- | --- | --- | --- |
| 예 | COMPLETE | 아니오 | 충족 | SATISFIED |
| 예 | COMPLETE | 아니오 | 부족 | UNSATISFIED |
| 예 | PARTIAL/UNKNOWN | 아니오 | 충족처럼 보임 | UNKNOWN |
| 예 | PARTIAL/UNKNOWN | 아니오 | 부족처럼 보임 | UNKNOWN |
| 예 | 무관 | 예, SELF_REPORTED | 무관 | UNKNOWN |
| 아니오 | 무관 | 무관 | 계산 불가 | UNKNOWN |

### Cycle 2 판정

보수적 판정 원칙을 적용하면 학교 API가 없어도 자가점검 용도로 사용할 수 있다. `ELIGIBLE`은 “사용자가 제공한 완전한 범위와 발행된 정책상 충족 예상”일 뿐 공식 졸업 가능 판정이 아니며, UI 면책문구를 제거하면 안 된다.

## 4. Cycle 3 — 보안·동시성·개인정보·운영성

### 확인 방법

1. 현재 `SecurityConfig`의 `ROLE_ROOT_ADMIN > ROLE_ADMIN` 계층과 `/api/admin/**` 규칙 대조
2. 학생 A/학생 B, 한성대 관리자/타 학교 관리자 간 IDOR 시나리오 검토
3. 프로필 수정과 평가 실행의 동시성, 정책 중복 발행 경쟁 검토
4. CSV 공격, 감사 로그, snapshot 개인정보, 데이터 보존 검토
5. 예상 주요 조회에 필요한 인덱스 검토

### 발견 및 조치

| 심각도 | 발견 | 위험 | 필수 구현·테스트 | 상태 |
| --- | --- | --- | --- | --- |
| P0 | repository를 public ID만으로 조회할 가능성 | 타 사용자·타 조직 자료 접근(IDOR) | 모든 조회를 actor user/organization 범위와 함께 수행 | API 반영 |
| P0 | 평가 도중 profile/과목 변경 가능 | hash와 item이 서로 다른 입력을 가리킴 | profile version 재확인, 한 transaction rollback | API 반영 |
| P0 | 겹치는 정책을 두 관리자가 동시에 발행 | 동일 조건 정책 2개 생성 | organization 잠금 + 겹침 조회 + DB unique 보조 | API/ERD 반영 |
| P1 | `/api/me/graduation/**`가 인증만 통과할 가능성 | ADMIN 계정의 학생 API 접근 | URL 규칙과 controller `@PreAuthorize(PARTICIPANT)` 이중화 | 구현 필수 |
| P1 | 관리자 endpoint에 내부 USER ID/학번 사용 | 열거·오조작 위험 | `profilePublicId` 사용, organization 재검증 | API 반영 |
| P1 | 평가 snapshot에 과도한 개인정보 저장 | 유출 범위 확대 | 이메일·메모·원본 CSV 제외, 학번 마스킹, 보존기간 정의 | ERD 반영 |
| P1 | 감사 로그 detail에 성적·증빙을 넣을 가능성 | 민감정보가 장기 로그에 남음 | 상태 변경 요약만 기록, AuditAction enum 추가 | 설계 반영 |
| P1 | CSV formula/대용량/인코딩 공격 | 관리자·학생 단말 공격 또는 자원 소모 | 2MB, 행 제한, UTF-8, formula prefix 무해화, 원본 미보관 | 기존/보강 필요 |
| P2 | 평가 이력 무제한 증가 | DB 용량과 조회 지연 | 같은 input/policy hash 결과 재사용, 보존·정리 정책 | 구현 결정 필요 |

### 필수 보안 테스트

- 학생 A 토큰으로 학생 B의 profile/course/evaluation public ID 조회·수정 시 404 또는 403
- 타 학교 ADMIN으로 한성대 정책·학생 검증 시 403
- PARTICIPANT가 정책 발행 API 호출 시 403
- ADMIN이 학생용 `/api/me/graduation/**` 호출 시 403
- 평가 요청과 profile 수정 경합 시 하나가 409 또는 rollback
- 동일 조건 정책 동시 발행 시 정확히 하나만 성공
- CSV의 `=`, `+`, `-`, `@` 시작 셀이 export/표시 과정에서 실행되지 않음

### Cycle 3 판정

현재 권한 체계와 통합할 수 있지만 public ID만 믿는 repository 메서드는 금지한다. 항상 로그인 actor의 user ID 또는 organization ID를 쿼리 조건에 포함해야 한다. `SecurityConfig`와 `@PreAuthorize`를 함께 적용한다.

## 5. 구현 전 차단 조건(P0)

다음 항목이 완료되지 않으면 구현 PR을 merge하지 않는다.

1. `ORGANIZATION.code` backfill + unique/not-null migration
2. 신규 테이블, FK, 삭제 동작, 인덱스를 정의한 MySQL migration
3. 입력 완전성에 따른 UNKNOWN 전파 단위 테스트
4. summary/detail 불일치 시 ELIGIBLE 차단 테스트
5. actor/organization 범위를 포함한 repository와 IDOR 통합 테스트
6. profile version 경합 및 정책 동시 발행 통합 테스트
7. 평가 snapshot 개인정보 최소화와 삭제·보존 정책 확정

## 6. 최종 승인 기준

- H2 단위 테스트만으로 승인하지 않고 MySQL 8 통합 테스트를 통과한다.
- 17~24학번, 편입, 조기졸업, 다중 트랙 fixture에 기대 상태가 명시돼 있다.
- 입력 누락 fixture는 부족이 아니라 UNKNOWN으로 판정된다.
- 평가 결과의 policy/input snapshot으로 당시 결과를 설명할 수 있다.
- 한성대 관리자와 실제 학생 샘플로 비교하되, 학교 API 동기화나 공식 졸업사정으로 표현하지 않는다.

이 기준을 통과하면 한성대 전용 자가점검 MVP 구현을 시작할 수 있다.
