# SW·AI중심대학 졸업요건 검사 설계

## 1. 목표와 범위

학생이 학교, 입학연도, 전공·트랙, 입학유형을 선택하고 성적표와 비교과 증빙을 입력하면 다음을 보여주는 기능을 설계한다.

- 충족한 졸업요건
- 부족한 학점·필수과목·비교과·인증
- 아직 자료가 없어 판단할 수 없는 요건
- 어떤 공식 문서와 정책 버전으로 판정했는지

초기 대상은 2025년 기준 SW중심대학 58개교다. 2026년에는 이 중 가천대·고려대·서강대·성균관대·순천향대·숭실대·연세대가 AI중심대학으로 전환됐고, 정부 자료는 `SW중심대학 50개교 + AI중심대학 7개교`로 표기한다. KAIST는 AI 단과대 분류를 별도로 추적한다. 따라서 사업 참여 여부는 `ORGANIZATION`의 영구 속성이 아니라 적용기간이 있는 별도 이력으로 관리한다.

이 기능의 결과는 **자가 점검용 예상 결과**다. 대학 학사 시스템의 공식 졸업사정을 대체하지 않는다.

## 2. 조사에서 확인한 핵심 문제

졸업요건은 학교 단위의 숫자 하나가 아니다.

- 한성대 공통 기준도 입학연도에 따라 총 130/140학점, 비교과 800점 적용 여부가 달라진다.
- 편입생은 등록학기와 비교과 기준이 별도다.
- 전공·트랙별 최소학점, 필수과목, 논문·작품·캡스톤 요건이 추가된다.
- 복수전공, 외국인전형, 교직, 조기졸업은 별도 조건이나 면제를 가진다.
- 같은 학교에서도 학과 공지가 바뀌며 특정 졸업예정 시점부터 새 요건이 적용된다.
- 서울시립대 경제학부처럼 전공필수 12학점, 전공선택 48학점, 상담과목 이수를 함께 요구하는 경우가 있다.
- 고려대 학과 사례처럼 영어성적, PBL, 캡스톤, 논문·특허·공모전 중 택일 같은 복합 조건이 존재한다.

따라서 PDF/공지 내용을 LLM이 매번 읽어 즉석 판정하는 방식은 재현성과 감사 가능성이 부족하다. 공식 원문을 수집한 뒤 사람이 검수한 **버전형 규칙 데이터**로 변환해야 한다.

## 3. 설계 원칙

1. **원문과 규칙을 분리한다.** 원문은 증거이고, 실행 규칙은 검수된 구조화 데이터다.
2. **정책은 수정하지 않고 새 버전을 발행한다.** 이미 수행한 판정이 나중에 달라지지 않게 한다.
3. **모르면 합격/불합격으로 추정하지 않는다.** `UNKNOWN`으로 반환한다.
4. **입학연도와 학적 조건을 먼저 고른다.** 이후 학점·과목·비교과 규칙을 평가한다.
5. **학교 공통 → 단과대 → 학과/전공 → 트랙 순으로 합성한다.** 하위 정책은 상위 정책을 암묵적으로 덮어쓰지 않는다.
6. **공식 출처와 검수자를 남긴다.** 모든 결과 항목에서 근거 문서로 역추적할 수 있어야 한다.
7. **성적표 원본은 최소 보관한다.** 파싱 후 암호화·보존기간·삭제 정책을 적용한다.

## 4. 도메인 경계

새 패키지는 `domain.graduation`으로 분리한다.

```text
domain.graduation
├── catalog       # 학교 사업 이력, 학과·전공·트랙, 교과목·동등과목
├── policy        # 원문, 정책 버전, 적용 조건, 규칙 트리
├── record        # 학생 학적, 이수과목, 비교과·자격 증빙
├── evaluation    # 규칙 평가기, 결과 스냅샷, 부족 항목
├── ingestion     # 원문 수집, 파싱 초안, 검수·발행
└── web           # 학생용·관리자용 API
```

기존 `ORGANIZATION`은 학교 식별자로 재사용한다. 현재 `USER.major` 문자열은 판정 키로 쓰기 어렵기 때문에, 정책 카탈로그의 `ACADEMIC_UNIT`과 연결되는 별도 학생 학적 프로필이 필요하다.

## 5. 데이터 모델

### 5.1 학교와 사업 이력

#### `UNIVERSITY_PROGRAM_MEMBERSHIP`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | BIGINT | PK |
| `organization_id` | BIGINT | `ORGANIZATION` FK |
| `program_type` | VARCHAR(30) | `SW_CENTERED`, `AI_CENTERED`, `AI_COLLEGE` |
| `track_type` | VARCHAR(20) | `GENERAL`, `SPECIALIZED`, nullable |
| `selected_year` | INT | 선정연도 |
| `valid_from` | DATE | 적용 시작 |
| `valid_to` | DATE | 종료, 현재면 null |
| `source_url` | VARCHAR(1000) | 공식 선정 근거 |
| `verified_at` | DATETIME | 확인 시각 |

`(organization_id, program_type, valid_from)`을 unique로 둔다.

### 5.2 학사 조직과 교과목

#### `ACADEMIC_UNIT`

- 학교 안의 단과대, 학부, 학과, 전공, 트랙을 트리로 저장한다.
- `unit_type`: `COLLEGE`, `DIVISION`, `DEPARTMENT`, `MAJOR`, `TRACK`
- `valid_from_year`, `valid_to_year`로 학과 개편과 명칭 변경을 보존한다.
- 같은 표시명이 여러 학교에 존재하므로 `(organization_id, external_code)`를 식별 기준으로 쓴다.

#### `COURSE_CATALOG`

- `organization_id`, `academic_year`, `course_code`, `name`, `credits`, `category`
- `category`: `GENERAL_REQUIRED`, `GENERAL_ELECTIVE`, `MAJOR_REQUIRED`, `MAJOR_ELECTIVE`, `FREE_ELECTIVE`, `TEACHING`
- 학교가 제공하는 교육과정표의 교과목 코드를 우선 사용한다.

#### `COURSE_EQUIVALENCE`

- 교과목 개편, 재수강, 대체과목, 편입 인정과목을 연결한다.
- `from_course_id`, `to_course_id`, `equivalence_type`, `valid_from`, `valid_to`, `approval_required`

### 5.3 출처와 정책 버전

#### `POLICY_SOURCE`

| 컬럼 | 설명 |
| --- | --- |
| `source_type` | `ACADEMIC_RULE`, `HANDBOOK`, `CURRICULUM`, `DEPARTMENT_NOTICE`, `FORM` |
| `official_url` | 대학 공식 원문 URL |
| `published_at` | 원문 게시일 |
| `retrieved_at` | 수집 시각 |
| `content_hash` | 수집 파일 SHA-256 |
| `stored_object_key` | 원문 보관 위치 |
| `parser_version` | 파서/추출기 버전 |
| `status` | `FETCHED`, `PARSED`, `REVIEWED`, `REJECTED` |

#### `GRADUATION_POLICY_VERSION`

| 컬럼 | 설명 |
| --- | --- |
| `organization_id` | 대상 학교 |
| `academic_unit_id` | 학교 공통이면 null, 학과·트랙 정책이면 FK |
| `version` | 학교·조직별 증가 번호 |
| `effective_from` / `effective_to` | 정책 유효기간 |
| `admission_year_from` / `admission_year_to` | 적용 학번 범위 |
| `status` | `DRAFT`, `IN_REVIEW`, `PUBLISHED`, `RETIRED` |
| `source_set_hash` | 사용한 원문 묶음 해시 |
| `reviewed_by`, `reviewed_at` | 검수자와 시각 |
| `supersedes_id` | 대체한 이전 버전 |

발행된 버전은 수정 금지하고 새 버전으로만 정정한다.

### 5.4 적용 조건과 규칙 트리

#### `POLICY_APPLICABILITY`

- `admission_type`: `FRESHMAN`, `TRANSFER`, `INTERNATIONAL`, `READMISSION`
- `degree_role`: `PRIMARY`, `DOUBLE_MAJOR`, `MINOR`
- `student_status`: 필요 시 교직·조기졸업·학석사연계 조건 추가
- 조건은 정책 선택에만 사용하고 학점 판정 로직과 섞지 않는다.

#### `REQUIREMENT_NODE`

규칙은 트리로 저장한다.

- 그룹: `ALL`, `ANY`, `N_OF`
- 수치: `TOTAL_CREDITS_MIN`, `CATEGORY_CREDITS_MIN`, `GPA_MIN`, `REGISTERED_SEMESTERS_MIN`, `ACTIVITY_POINTS_MIN`, `VOLUNTEER_HOURS_MIN`
- 교과: `COURSE_ALL`, `COURSE_ANY`, `COURSE_N_OF`, `CAPSTONE_COMPLETED`
- 증빙: `LANGUAGE_SCORE`, `CERTIFICATE_ANY`, `THESIS_APPROVED`, `PORTFOLIO_APPROVED`, `GRADUATION_EXAM_PASSED`
- 예외: `MANUAL_REVIEW`

노드는 `parent_id`, `sequence_no`, `rule_type`, `parameters_json`, `source_id`, `source_locator`를 가진다. `source_locator`에는 PDF 페이지나 HTML 절 제목을 넣는다.

`parameters_json` 예시:

```json
{
  "category": "MAJOR_REQUIRED",
  "minimumCredits": 12,
  "rounding": "EXACT"
}
```

복합 택일 예시는 `ANY` 아래에 `LANGUAGE_SCORE`, `CERTIFICATE_ANY`, `THESIS_APPROVED`를 둔다. 복잡한 규칙을 문자열 수식으로 실행하지 않아 관리자 입력을 통한 코드 실행을 차단한다.

### 5.5 학생 이수정보

#### `STUDENT_ACADEMIC_PROFILE`

- `user_id`, `organization_id`, `student_number`, `admission_year`, `admission_type`
- 주전공·복수전공·부전공 `ACADEMIC_UNIT` 연결
- `curriculum_year`: 학교가 학번과 다른 교육과정 연도를 지정할 때 사용
- `expected_graduation_term`

#### `COURSE_COMPLETION`

- `course_code`, `course_name_snapshot`, `credits`, `grade`, `term`, `completion_status`
- 원본 성적표 값과 정규화된 카탈로그 매핑을 함께 보관한다.
- 매핑이 불확실하면 임의 합산하지 않고 `mapping_status=NEEDS_REVIEW`로 둔다.

#### `NON_COURSE_EVIDENCE`

- `evidence_type`, `issuer`, `name`, `score`, `unit`, `issued_at`, `expires_at`
- `verification_status`: `SELF_REPORTED`, `DOCUMENT_VERIFIED`, `UNIVERSITY_VERIFIED`, `REJECTED`
- 민감한 원본 파일은 별도 암호화 저장소와 짧은 보존기간을 사용한다.

### 5.6 판정 스냅샷

#### `GRADUATION_EVALUATION`

- 학생 입력 해시, 정책 버전 ID 목록, 평가기 버전, 실행시각을 저장한다.
- 전체 상태: `ELIGIBLE`, `NOT_ELIGIBLE`, `INDETERMINATE`

#### `REQUIREMENT_RESULT`

- 상태: `SATISFIED`, `UNSATISFIED`, `UNKNOWN`, `NOT_APPLICABLE`
- 현재값, 기준값, 부족값, 설명, 근거 URL, source locator를 저장한다.
- 하나라도 필수 규칙이 `UNKNOWN`이면 전체 결과를 `ELIGIBLE`로 만들지 않는다.

## 6. 정책 선택과 평가 알고리즘

1. 학생의 학교, 입학연도, 입학유형, 학위 역할, 학사 조직을 검증한다.
2. 평가 기준일에 발행 상태인 학교 공통 정책을 선택한다.
3. 단과대 → 학부/학과 → 전공/트랙 정책을 순서대로 추가한다.
4. 적용 조건이 겹치는 발행 정책이 둘 이상이면 자동 판정을 중단하고 `POLICY_CONFLICT`를 반환한다.
5. 성적표 과목을 교과 카탈로그와 동등과목표에 매핑한다.
6. 같은 과목·재수강·대체과목의 중복 학점을 학교 규칙에 따라 제거한다.
7. 규칙 트리를 leaf부터 평가하고 그룹 상태를 계산한다.
8. 결과와 사용한 원문·정책·입력 해시를 스냅샷으로 저장한다.

### 상태 결합

- `ALL`: 하나라도 `UNSATISFIED`면 미충족, 없고 `UNKNOWN`이 있으면 미확정
- `ANY`: 하나라도 `SATISFIED`면 충족, 모두 미충족일 때만 미충족
- `N_OF`: 충족 수가 N 이상이면 충족, 남은 미확정으로 N 달성이 가능하면 미확정
- `MANUAL_REVIEW`: 학교 확인값이 없으면 항상 `UNKNOWN`

## 7. 한성대학교 파일럿 규칙

한성대 공식 페이지에서 확인되는 학교 공통 규칙을 첫 번째 파일럿으로 사용한다.

| 대상 | 규칙 |
| --- | --- |
| 신입학 일반 | 등록 8학기 이상 |
| 2016학번 이후 | 총 130학점 이상 |
| 2015학번 이전 | 총 140학점 이상 |
| 전체 | 총 평점평균 2.0 이상 |
| 2016학번 이후 일반 | 비교과 800점 이상 |
| 일반편입 | 본교 등록 4학기 이상, 총 130/140학점, 비교과 400점 |
| 학과·트랙 | 학번별 교양·전공 최저학점 및 논문·작품·자격 요건 추가 |

로봇 관련 트랙 공식 페이지에는 15개 개설 과목 중 13개(39학점) 이상, 기초과목, 지정 필수군, 캡스톤·졸업논문 과목 같은 하위 정책 사례가 있다. 이처럼 학교 공통 규칙만 통과해도 전공 정책 자료가 없으면 결과는 `INDETERMINATE`여야 한다.

실행 가능한 예시는 [graduation-rule-example-hansung.json](./graduation-rule-example-hansung.json)에 둔다. 이 파일은 데이터 형식 검증용 초안이며 학교의 공식 졸업판정 결과가 아니다.

## 8. 58개교 수집 전략

대상 레지스트리는 [sw-centered-universities-2025.csv](./sw-centered-universities-2025.csv)에 둔다. 한 번에 58개교 전체를 크롤링해 공개하지 않고 다음 단계로 진행한다.

### 단계 A — 학교 공통 규정

각 학교마다 다음 공식 자료를 우선순위대로 수집한다.

1. 학칙·학사운영규정
2. 해당 학년도 학사편람·수강편람
3. 교육과정표
4. 졸업 안내 공식 페이지
5. 학과·전공 공지와 제출 양식

학교 공통 총학점, GPA, 등록학기, 교양, 비교과, 외국어 기준까지 구조화한다.

### 단계 B — SW 관련 학사조직

각 대학의 SW전공, 컴퓨터공학, AI, 소프트웨어융합 학부·학과·트랙부터 적용한다. 58개교의 모든 학과를 동시에 지원하는 것은 범위가 지나치게 크므로, SW중심대학 참여 학생이 실제 사용하는 조직부터 순차 발행한다.

### 단계 C — 예외와 다전공

편입, 외국인, 복수전공, 부전공, 교직, 조기졸업, 학석사연계를 추가한다.

### 수집 상태

- `DISCOVERED`: 학교와 공식 학사 사이트 확인
- `SOURCES_COLLECTED`: 원문 확보 및 해시 저장
- `PARSED`: 규칙 초안 생성
- `REVIEW_REQUIRED`: 충돌·모호성 검토 중
- `PUBLISHED`: 2인 검수 후 학생 판정에 사용
- `STALE`: 새 학년도 자료 발견 또는 원문 변경

매일 원문 해시를 비교할 필요는 없다. 학기 시작 전과 졸업사정 공지 시점에 재수집하고, 학교 RSS/게시판 변경 감지를 보조적으로 사용한다.

## 9. API 초안

### 학생용

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/graduation/universities` | 지원 학교와 정책 공개 상태 조회 |
| `GET` | `/api/graduation/universities/{publicId}/academic-units` | 학과·전공·트랙 검색 |
| `GET` | `/api/graduation/policies/resolve` | 학번·전공·입학유형에 적용될 정책 미리보기 |
| `POST` | `/api/graduation/records/transcript/parse` | 성적표 임시 파싱, 사용자 확인값 반환 |
| `PUT` | `/api/graduation/me/academic-profile` | 학적 프로필 저장 |
| `PUT` | `/api/graduation/me/course-completions` | 확인된 이수과목 저장 |
| `POST` | `/api/graduation/me/evidences` | 비교과·자격·논문 증빙 등록 |
| `POST` | `/api/graduation/me/evaluations` | 새 판정 실행 |
| `GET` | `/api/graduation/me/evaluations/{publicId}` | 근거와 부족 항목 조회 |

### 관리자용

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `POST` | `/api/admin/graduation/sources` | 공식 원문 등록·수집 |
| `POST` | `/api/admin/graduation/policies` | 정책 초안 생성 |
| `PUT` | `/api/admin/graduation/policies/{id}/rules` | 규칙 트리 편집 |
| `POST` | `/api/admin/graduation/policies/{id}/request-review` | 검수 요청 |
| `POST` | `/api/admin/graduation/policies/{id}/publish` | 검수된 버전 발행 |
| `POST` | `/api/admin/graduation/policies/{id}/retire` | 이전 버전 종료 |

정책 작성자와 승인자는 분리하고 모든 변경을 감사 로그에 남긴다.

## 10. 응답 예시

```json
{
  "status": "INDETERMINATE",
  "policyAsOf": "2026-08-11",
  "summary": {
    "satisfied": 3,
    "unsatisfied": 1,
    "unknown": 1
  },
  "requirements": [
    {
      "code": "TOTAL_CREDITS",
      "status": "UNSATISFIED",
      "current": 124,
      "required": 130,
      "remaining": 6,
      "message": "총 이수학점이 6학점 부족합니다."
    },
    {
      "code": "TRACK_GRADUATION_REQUIREMENT",
      "status": "UNKNOWN",
      "message": "선택한 트랙의 검수된 정책이 아직 없습니다. 학교 학과사무실 확인이 필요합니다."
    }
  ],
  "disclaimer": "자가 점검 결과이며 대학의 공식 졸업사정을 대체하지 않습니다."
}
```

## 11. 성적표 가져오기

초기에는 학교 포털 계정이나 비밀번호를 받지 않는다.

1. 학생이 성적증명서 PDF/CSV를 직접 업로드한다.
2. 서버는 텍스트 레이어 → 표 추출 → OCR 순으로 시도한다.
3. 파싱 결과를 학생이 반드시 확인·수정한다.
4. 과목 코드가 없거나 동명이면 자동 확정하지 않는다.
5. 원본은 파싱 완료 후 즉시 삭제하거나 명시적 동의가 있을 때만 짧게 보관한다.

향후 대학이 공식 API를 제공하면 `TranscriptProviderPort` 어댑터로 연결한다. 비공식 포털 스크래핑과 로그인 자동화는 보안·약관·유지보수 문제 때문에 기본안에서 제외한다.

## 12. 보안·개인정보·운영 기준

- 학번, 성적, 자격증은 개인정보로 분류하고 전송·저장 시 암호화한다.
- 관리자도 업무상 필요한 학교의 자료만 조회하도록 organization scope를 적용한다.
- 공개 정책 데이터와 학생 성적 데이터를 물리적·논리적으로 분리한다.
- 업로드 파일 확장자·MIME·크기·악성코드를 검사한다.
- 정책 발행, 증빙 검증, 수동 판정은 `ADMIN_AUDIT_LOG`에 기록한다.
- 결과 화면에 정책 기준일, 출처, 미확정 항목, 면책문구를 항상 표시한다.
- 원문 삭제나 링크 단절에 대비해 해시와 허용 범위 내 보관 사본을 유지한다.

## 13. 구현 순서

### 1차 MVP — 한성대

- 테이블과 규칙 평가기
- 한성대 학교 공통 정책
- 한성대 SW 관련 1~2개 트랙 정책
- 수동 과목 입력과 JSON/CSV import
- 부족 요건 및 근거 표시

### 2차 — 58개교 학교 공통

- 학교 레지스트리와 공식 출처 수집 도구
- 학교 공통 총학점·GPA·등록학기 정책
- 지원 수준을 `공통만`, `SW전공 포함`, `전체 검수`로 표시

### 3차 — 전공·예외 확대

- SW 관련 학과·트랙
- 편입·외국인·복수전공
- PDF/OCR 성적표 파서와 관리자 검수 UI

### 4차 — 대학 연동

- 공식 API/CSV 연동
- 대학 담당자 정책 승인
- 학사편람 갱신 감지와 영향 학생 재평가 알림

## 14. 인수 기준

- 같은 입력과 같은 정책 버전은 항상 같은 결과를 낸다.
- 결과의 모든 항목에서 공식 출처로 이동할 수 있다.
- 정책 충돌이나 자료 누락을 합격으로 처리하지 않는다.
- 입학연도, 편입, 복수전공 조건별 테스트가 있다.
- 발행 정책은 수정 불가이며 새 버전으로만 대체된다.
- 한성대 파일럿 샘플 20건에서 학사 담당자 수동 판정과 일치한다.
- 58개교별 지원 수준과 마지막 검수일이 공개된다.

## 15. 조사 출처

- AI·SW중심대학협의회 선정대학: <https://www.swuniv.kr/organization>
- 2025년 58개교 목록 교차 확인: <https://community.linkareer.com/employment_data/5302406>
- 2026년 AI중심대학 전환 7개교: <https://www.etnews.com/20260505000001>
- 2026년 정부 사업 규모(`SW 50`, `AI 10`): <https://www.mafra.go.kr/bbs/home/792/596938/download.do>
- 한성대 공통 졸업 심사기준: <https://www.hansung.ac.kr/cis/3224/subview.do>
- 한성대 2026년 졸업사정 안내: <https://www.hansung.ac.kr/bbs/hansung/2127/222590/artclView.do>
- 한성대 트랙 졸업요건 사례: <https://hansung.ac.kr/Engineering/4941/subview.do>
- 서울시립대 경제학부 졸업요건 사례: <https://econ.uos.ac.kr/undergraduate-graduation-requirements>
- 고려대 학과별 복합 졸업요건 사례: <https://gup.korea.ac.kr/bbs/am/421/169193/download.do>

공식 적용 전에는 각 대학 학사 담당자가 해당 학년도 원문과 규칙 변환 결과를 다시 확인해야 한다.
