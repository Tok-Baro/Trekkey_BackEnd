# 한성대학교 졸업요건 API 및 프론트 계약 명세

## 1. 공통 규칙

- Base URL: `/api`
- 학생 API: `ROLE_PARTICIPANT`
- 관리자 API: `ROLE_ADMIN`, 조직 범위 적용
- ID: 외부에는 UUID 문자열 `publicId`
- 날짜: `yyyy-MM-dd`
- 학기: `yyyy-1`, `yyyy-2`, `yyyy-S`, `yyyy-W`
- 학점: JSON number, 소수점 1자리 허용
- 모든 응답은 기존 `SuccessResponse<T>` 또는 `ErrorResponse`를 사용

성공 응답 예시:

```json
{
  "isSuccess": true,
  "timestamp": "2026-08-11 15:10:00",
  "code": "SUCCESS_OK",
  "httpStatus": 200,
  "message": "요청에 성공했습니다.",
  "data": {}
}
```

## 2. 학생 API

### 2.1 학적 프로필 조회

`GET /api/me/graduation/profile`

프로필이 없으면 `data.configured=false`로 200을 반환한다. 최초 방문을 404 오류 화면으로 만들지 않는다.

```json
{
  "configured": true,
  "profilePublicId": "8c0fe80a-1884-4fd0-a46f-7085be90f621",
  "organization": {
    "publicId": "hansung-organization-public-id",
    "name": "한성대학교"
  },
  "studentNumber": "2171193",
  "admissionYear": 2021,
  "curriculumYear": 2021,
  "admissionType": "FRESHMAN",
  "graduationPath": "REGULAR",
  "majorPlanType": "CONVERGENCE_I",
  "registeredSemesters": 7,
  "totalCredits": 124.0,
  "hansungCredits": 124.0,
  "transferRecognizedCredits": 0.0,
  "cumulativeGpa": 3.42,
  "gpaScale": 4.5,
  "activityPoints": 720,
  "internationalStudent": false,
  "teachingProgram": false,
  "academicUnits": [
    {
      "publicId": "unit-uuid-1",
      "name": "모바일소프트웨어트랙",
      "roleType": "PRIMARY",
      "graduationEvidenceStatus": "SELF_REPORTED"
    },
    {
      "publicId": "unit-uuid-2",
      "name": "웹공학트랙",
      "roleType": "SECONDARY",
      "graduationEvidenceStatus": "UNKNOWN"
    }
  ],
  "version": 3
}
```

### 2.2 학적 프로필 저장

`PUT /api/me/graduation/profile`

```json
{
  "admissionYear": 2021,
  "curriculumYear": 2021,
  "admissionType": "FRESHMAN",
  "graduationPath": "REGULAR",
  "majorPlanType": "CONVERGENCE_I",
  "registeredSemesters": 7,
  "totalCredits": 124.0,
  "hansungCredits": 124.0,
  "transferRecognizedCredits": 0.0,
  "cumulativeGpa": 3.42,
  "gpaScale": 4.5,
  "activityPoints": 720,
  "internationalStudent": false,
  "teachingProgram": false,
  "academicUnits": [
    {"academicUnitPublicId": "unit-uuid-1", "roleType": "PRIMARY"},
    {"academicUnitPublicId": "unit-uuid-2", "roleType": "SECONDARY"}
  ],
  "version": 3
}
```

검증:

- 로그인 사용자의 학교가 한성대학교여야 함
- `admissionYear`: 1900~현재연도
- `curriculumYear`: 입학연도 이상 변경 시 관리자 이수인정 근거 필요
- 학점·포인트·학기 0 이상
- GPA는 scale 이하
- 전공 이수유형에 필요한 academic unit 역할 수 검증
- version 불일치 시 409

### 2.3 한성대 학사조직 검색

`GET /api/graduation/hansung/academic-units?keyword=소프트웨어&admissionYear=2021&unitType=TRACK`

```json
[
  {
    "publicId": "unit-uuid-1",
    "name": "모바일소프트웨어트랙",
    "unitType": "TRACK",
    "parentName": "IT공과대학",
    "validFromYear": 2017,
    "validToYear": null
  }
]
```

인증된 참가자만 허용한다. 학교는 URL에서 자유롭게 받지 않고 한성대 카탈로그로 고정한다.

### 2.4 적용 정책 미리보기

`GET /api/me/graduation/policies/resolve`

프로필 설정값으로 현재 적용할 정책을 조회한다.

```json
{
  "policyAsOf": "2026-08-11",
  "resolvable": true,
  "policies": [
    {
      "publicId": "policy-common-uuid",
      "policyCode": "HANSUNG-COMMON-2016+",
      "version": 1,
      "title": "16학번 이후 학교 공통 졸업요건"
    },
    {
      "publicId": "policy-general-uuid",
      "policyCode": "HANSUNG-GENERAL-2019-2023",
      "version": 1,
      "title": "19~23학번 교양 이수요건"
    },
    {
      "publicId": "policy-major-uuid",
      "policyCode": "HANSUNG-MAJOR-CONVERGENCE-I-2017+",
      "version": 1,
      "title": "17학번 이후 융합전공 I"
    }
  ],
  "warnings": [
    {
      "code": "UNIT_POLICY_MISSING",
      "message": "웹공학트랙 졸업작품 정책은 학교 확인이 필요합니다."
    }
  ]
}
```

### 2.5 이수과목 목록

`GET /api/me/graduation/courses`

```json
[
  {
    "publicId": "course-record-uuid",
    "term": "2024-1",
    "courseCode": "CAA0001",
    "courseName": "AI와 SW 기초",
    "credits": 3.0,
    "grade": "A+",
    "completionStatus": "COMPLETED",
    "category": "GENERAL_REQUIRED",
    "academicUnit": null,
    "mappingStatus": "MATCHED",
    "retakeGroupKey": null
  }
]
```

### 2.6 이수과목 일괄 저장

`PUT /api/me/graduation/courses`

전체 교체가 아니라 `upserts`와 `deletePublicIds`를 사용해 충돌과 실수 삭제를 줄인다.

```json
{
  "upserts": [
    {
      "publicId": null,
      "term": "2024-1",
      "courseCode": "CAA0001",
      "courseName": "AI와 SW 기초",
      "credits": 3.0,
      "grade": "A+",
      "completionStatus": "COMPLETED",
      "category": "GENERAL_REQUIRED",
      "academicUnitPublicId": null,
      "retakeGroupKey": null
    }
  ],
  "deletePublicIds": []
}
```

응답:

```json
{
  "saved": 1,
  "deleted": 0,
  "warnings": []
}
```

중복·재수강·카탈로그 불일치는 400 대신 저장 가능한 범위는 저장하고 warning으로 돌려준다. 학점이 음수거나 필수값이 없으면 전체 요청을 400으로 거절한다.

### 2.7 CSV import 미리보기

`POST /api/me/graduation/courses/import-preview`

- Content-Type: `multipart/form-data`
- field: `file`
- 허용: CSV, 최대 2MB
- 서버 저장 없음

```json
{
  "rows": [
    {
      "rowNumber": 2,
      "status": "MATCHED",
      "term": "2024-1",
      "courseCode": "CAA0001",
      "courseName": "AI와 SW 기초",
      "credits": 3.0,
      "suggestedCategory": "GENERAL_REQUIRED",
      "messages": []
    }
  ],
  "summary": {
    "total": 1,
    "matched": 1,
    "needsReview": 0,
    "invalid": 0
  }
}
```

preview 결과는 자동 저장하지 않는다. 사용자가 확인 후 2.6 API를 호출한다.

### 2.8 비교과·수동요건 조회

`GET /api/me/graduation/non-course-records`

### 2.9 비교과·수동요건 저장

`PUT /api/me/graduation/non-course-records`

```json
{
  "records": [
    {
      "publicId": null,
      "recordType": "TOPIK",
      "title": "한국어능력시험",
      "numericValue": 4,
      "issuedAt": "2026-04-10",
      "expiresAt": null
    },
    {
      "publicId": null,
      "recordType": "GRADUATION_WORK",
      "title": "모바일소프트웨어트랙 졸업작품",
      "numericValue": null,
      "issuedAt": null,
      "expiresAt": null
    }
  ]
}
```

학생 저장은 항상 `SELF_REPORTED`다. 요청으로 `UNIVERSITY_VERIFIED`를 설정할 수 없다.

### 2.10 졸업요건 검사 실행

`POST /api/me/graduation/evaluations`

```json
{
  "policyAsOf": "2026-08-11"
}
```

`policyAsOf` 생략 시 서버 현재 날짜다. 과거 정책 재현 목적 외에는 미래 날짜를 금지한다.

응답 `201 Created`:

```json
{
  "evaluationPublicId": "evaluation-uuid",
  "status": "NOT_ELIGIBLE",
  "policyAsOf": "2026-08-11",
  "evaluatedAt": "2026-08-11T15:10:00",
  "summary": {
    "satisfied": 5,
    "unsatisfied": 2,
    "unknown": 1
  },
  "progress": {
    "totalCredits": {"current": 124.0, "required": 130.0},
    "generalCredits": {"current": 28.0, "required": 28.0},
    "primaryMajorCredits": {"current": 36.0, "required": 39.0},
    "secondaryMajorCredits": {"current": 39.0, "required": 39.0},
    "activityPoints": {"current": 720, "required": 800},
    "gpa": {"current": 3.42, "required": 2.0}
  },
  "requirements": [
    {
      "code": "TOTAL_CREDITS",
      "title": "총 이수학점",
      "status": "UNSATISFIED",
      "currentValue": "124",
      "requiredValue": "130",
      "remainingValue": "6",
      "message": "총 이수학점이 6학점 부족합니다.",
      "source": {
        "title": "한성대학교 졸업 및 학위",
        "url": "https://www.hansung.ac.kr/hansung/6234/subview.do",
        "locator": "졸업사정 및 심사기준"
      }
    },
    {
      "code": "SECONDARY_TRACK_GRADUATION_WORK",
      "title": "제2트랙 졸업요건",
      "status": "UNKNOWN",
      "message": "학과사무실 또는 종합정보시스템에서 합격 여부를 확인하세요.",
      "source": null
    }
  ],
  "disclaimer": "자가점검 결과이며 한성대학교의 공식 졸업사정을 대체하지 않습니다."
}
```

### 2.11 최신 결과 조회

`GET /api/me/graduation/evaluations/latest`

없으면 `data=null`로 200을 반환한다.

### 2.12 과거 결과 조회

`GET /api/me/graduation/evaluations/{evaluationPublicId}`

본인 결과만 조회할 수 있다. 현재 정책이 바뀌어도 당시 snapshot을 반환한다.

## 3. 관리자 API

### 3.1 정책 목록

`GET /api/admin/graduation/policies?status=DRAFT&policyType=COMMON`

### 3.2 정책 초안 생성

`POST /api/admin/graduation/policies`

### 3.3 규칙 트리 저장

`PUT /api/admin/graduation/policies/{policyPublicId}/requirements`

### 3.4 검수 요청

`POST /api/admin/graduation/policies/{policyPublicId}/request-review`

### 3.5 정책 발행

`POST /api/admin/graduation/policies/{policyPublicId}/publish`

- 자기 자신이 작성한 정책을 혼자 발행하지 못하도록 작성자/검수자 분리 권장
- 조건이 겹치는 발행 정책이 있으면 409
- source hash와 rule validation이 완료되지 않으면 400

### 3.6 학생 수동요건 검증

`PATCH /api/admin/graduation/students/{studentId}/non-course-records/{recordPublicId}`

```json
{
  "verificationStatus": "UNIVERSITY_VERIFIED",
  "note": "2026-1 트랙 졸업작품 합격 확인"
}
```

관리자의 organization과 학생 organization이 다르면 403이다.

## 4. 오류 코드

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `GRADUATION_UNSUPPORTED_ORGANIZATION` | 403 | 한성대학교 대상 계정 아님 |
| `GRADUATION_PROFILE_NOT_CONFIGURED` | 400 | 검사 전 학적 설정 필요 |
| `GRADUATION_PROFILE_VERSION_CONFLICT` | 409 | profile optimistic lock 충돌 |
| `GRADUATION_INVALID_MAJOR_PLAN` | 400 | 전공 이수유형과 트랙 구성이 맞지 않음 |
| `GRADUATION_ACADEMIC_UNIT_NOT_FOUND` | 404 | 유효한 학사조직 없음 |
| `GRADUATION_POLICY_NOT_FOUND` | 404 | 적용 가능한 발행 정책 없음 |
| `GRADUATION_POLICY_CONFLICT` | 409 | 중복 적용 정책 존재 |
| `GRADUATION_POLICY_IMMUTABLE` | 409 | 발행 정책 수정 시도 |
| `GRADUATION_POLICY_SOURCE_REQUIRED` | 400 | 출처 없는 정책 발행 |
| `GRADUATION_COURSE_DUPLICATED` | 409 | 동일 학기·코드 중복 |
| `GRADUATION_COURSE_MAPPING_REQUIRED` | 400 | 평가에 필수인 과목 매핑 미확정 |
| `GRADUATION_IMPORT_FILE_INVALID` | 400 | CSV 형식·크기 오류 |
| `GRADUATION_EVALUATION_NOT_FOUND` | 404 | 결과 없음 또는 타인 결과 |
| `GRADUATION_EVALUATION_INPUT_CHANGED` | 409 | 검사 중 입력 변경 |

## 5. 프론트 구현 계약

### 5.1 라우트

`src/routeConfig.js`

```js
{ id: "graduation", path: "/participant/graduation", label: "졸업요건" }
```

`src/pages/participant/ParticipantPortal.jsx`

```js
{ id: "graduation", label: "졸업요건", icon: GraduationCap }
```

portal 내부 분기:

```jsx
{activeView === "graduation" && <GraduationRequirementsView />}
```

### 5.2 API 모듈

`graduationApi.js`는 현재 `backendApi.js`의 인증 request를 재사용할 수 있도록 공통 `apiRequest`를 export하거나, graduation 함수를 `backendApi.js`에 먼저 두고 추후 모듈화한다. refresh·401 event 흐름을 별도로 구현하지 않는다.

필수 함수:

```text
getGraduationProfile
saveGraduationProfile
searchHansungAcademicUnits
resolveGraduationPolicies
listGraduationCourses
saveGraduationCourses
previewGraduationCourseImport
listGraduationNonCourseRecords
saveGraduationNonCourseRecords
runGraduationEvaluation
getLatestGraduationEvaluation
```

### 5.3 `useGraduationData`

상태:

```text
profile
courses
nonCourseRecords
evaluation
resolvedPolicies
isInitialLoading
isSavingProfile
isSavingCourses
isEvaluating
error
```

초기 로딩은 profile → profile이 있으면 courses/non-course/latest evaluation을 병렬 조회한다. 화면 진입 때마다 자동으로 새 평가를 생성하지 않는다.

mutation 성공 후:

- profile/course/evidence 변경: 기존 evaluation에 `stale=true` 표시
- 사용자가 `다시 검사`를 눌러야 새 evaluation 생성
- 중복 클릭 방지
- unmount 이후 state update 방지

### 5.4 표시 매핑

```js
const evaluationStatus = {
  ELIGIBLE: "졸업 가능 예상",
  NOT_ELIGIBLE: "요건 부족",
  INDETERMINATE: "확인 필요"
};

const requirementStatus = {
  SATISFIED: "충족",
  UNSATISFIED: "미충족",
  UNKNOWN: "확인 필요",
  NOT_APPLICABLE: "해당 없음"
};
```

서버 enum을 프론트가 임의 재해석하지 않는다.

### 5.5 빈 상태와 오류 상태

- profile 없음: 학적 설정 CTA
- course 없음: 직접 입력/CSV CTA
- evaluation 없음: `졸업요건 검사하기`
- 403 unsupported: 한성대 전용 기능 설명
- 409 input changed: 최신 입력 재조회 후 다시 검사 안내
- policy missing: 고객 오류가 아니라 `확인 필요` 안내

## 6. 테스트 계약

### 백엔드

- 2021학번 융합전공 I, 130학점/800점/GPA 2.0
- 2015학번 140학점, 비교과 미적용
- 일반편입과 학사편입 학점 기준 차이
- 외국인 2/3/4학년 편입 분기
- 조기졸업 GPA/F 조건
- 학석사 7·8학기 GPA와 대학원 과목 조건
- 제2트랙 수동요건 UNKNOWN 전파
- 발행 정책 불변성과 정책 충돌
- 타 사용자/타 학교 접근 차단

### 프론트

- route와 sidebar 활성 상태
- 최초 profile 설정
- 전공 이수유형별 필드 조건부 표시
- 과목 추가·삭제·중복 경고
- CSV preview 후 확인 저장
- 미충족 우선 정렬
- stale evaluation 표시
- mobile layout과 keyboard navigation

## 7. 인수 예시

2021학번 신입학, 융합전공 I 학생이 다음 값을 입력한다.

```text
등록학기 8
총학점 130
GPA 3.2
비교과 800
교양 28
제1트랙 39(기초 3, 필수 15 포함)
제2트랙 39(기초 3, 필수 15 포함)
제1트랙 졸업작품 학교확인 완료
제2트랙 졸업작품 자가신고만 완료
```

예상 결과:

- 수치·교과요건: `SATISFIED`
- 제1트랙 졸업요건: `SATISFIED`
- 제2트랙 졸업요건: `UNKNOWN`
- 전체: `INDETERMINATE`

제2트랙 확인값이 `UNIVERSITY_VERIFIED`가 된 뒤 다시 검사하면 `ELIGIBLE`이 된다.
