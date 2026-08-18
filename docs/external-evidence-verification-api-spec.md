# 외부 증빙 검증 API 및 프론트 계약

## 1. 공통 원칙

- Base URL: `/api`
- 학생은 본인 submission만, 관리자는 본인 organization queue만 조회한다.
- 응답 ID는 UUID `publicId`다.
- 업로드는 `initiate → object storage upload → complete` 3단계로 하며 애플리케이션 서버에 대용량 multipart를 장시간 유지하지 않는다.
- 클라이언트는 `verificationStatus`, `assuranceLevel`, reviewer를 설정할 수 없다.
- 문서 원문과 OCR 원문은 목록 응답에 넣지 않는다.
- 상태 변경 API는 idempotency key와 optimistic version을 사용한다.

## 2. 학생 API

### 2.1 제출 초안 생성

`POST /api/me/evidence-submissions`

```json
{
  "evidenceType": "QUALIFICATION",
  "title": "정보처리기사",
  "issuerCode": "HRDK_QNET",
  "issuerName": "한국산업인력공단",
  "credentialNumber": "비공개 입력값",
  "issuedAt": "2026-06-12",
  "expiresAt": null,
  "claim": {
    "schemaType": "NATIONAL_TECHNICAL_QUALIFICATION",
    "schemaVersion": 1,
    "qualificationName": "정보처리기사",
    "grade": null
  }
}
```

응답:

```json
{
  "publicId": "submission-uuid",
  "status": "DRAFT",
  "version": 0,
  "requiredFiles": ["ORIGINAL"],
  "acceptedMediaTypes": ["application/pdf", "image/jpeg", "image/png"],
  "maximumBytesPerFile": 10485760
}
```

서버가 organization과 subject binding을 로그인 사용자로 정한다. request의 issuer 이름은 표시 snapshot이며, 검증 adapter 선택은 `issuerCode` registry로 한다.

### 2.2 업로드 URL 발급

`POST /api/me/evidence-submissions/{submissionPublicId}/files/initiate`

```json
{
  "fileRole": "ORIGINAL",
  "filename": "certificate.pdf",
  "mediaType": "application/pdf",
  "byteSize": 482913,
  "sha256Base64": "base64-sha256"
}
```

응답의 `uploadUrl`은 수분 내 만료되고 정해진 object key, size, content type에만 유효하다.

### 2.3 업로드 완료

`POST /api/me/evidence-submissions/{submissionPublicId}/files/{filePublicId}/complete`

```json
{
  "etag": "storage-etag",
  "version": 0
}
```

서버가 storage metadata, 실제 크기, hash를 다시 확인한다. 클라이언트 hash만 믿지 않는다. 완료 후 악성코드/OCR 비동기 작업이 시작된다.

### 2.4 제출 확정

`POST /api/me/evidence-submissions/{submissionPublicId}/submit`

```json
{
  "consentVersion": "external-evidence-v1",
  "purposes": ["GRADUATION_REQUIREMENT"],
  "version": 1
}
```

사전조건: 필수 파일 존재, malware 검사 통과, claim schema 검증, 개인정보 처리 동의. 성공하면 `202 Accepted`, `SUBMITTED` 또는 `AUTOMATED_CHECKING`을 반환한다.

### 2.5 목록·상세

- `GET /api/me/evidence-submissions?status=VERIFIED&page=0&size=20`
- `GET /api/me/evidence-submissions/{submissionPublicId}`

```json
{
  "publicId": "submission-uuid",
  "evidenceType": "QUALIFICATION",
  "title": "정보처리기사",
  "issuerName": "한국산업인력공단",
  "status": "MANUAL_REVIEW",
  "verification": {
    "decision": null,
    "assuranceLevel": "L1",
    "requiredAssuranceLevel": "L3",
    "reasonCode": "OFFICIAL_CHANNEL_REQUIRES_REVIEW",
    "nextAction": "WAIT_FOR_REVIEW",
    "validUntil": null
  },
  "files": [
    {
      "publicId": "file-uuid",
      "fileRole": "ORIGINAL",
      "filename": "certificate.pdf",
      "malwareStatus": "CLEAN",
      "ocrStatus": "DONE"
    }
  ],
  "timeline": [
    {"status": "SUBMITTED", "occurredAt": "2026-08-18T04:10:00Z"},
    {"status": "MANUAL_REVIEW", "occurredAt": "2026-08-18T04:11:03Z"}
  ],
  "version": 2
}
```

외부 provider의 원문 응답, 내부 risk score, reviewer 개인정보는 학생 응답에서 제외한다.

### 2.6 정정·취소

- `POST /api/me/evidence-submissions/{id}/supersede`: 기존 제출을 수정하지 않고 새 draft 생성
- `POST /api/me/evidence-submissions/{id}/withdraw`: 결정 전 사용자 철회

이미 졸업요건이나 Credential에 연결된 `VERIFIED` 건은 사용자가 삭제할 수 없다. 개인정보 삭제 요청은 별도 보존정책 workflow로 처리한다.

## 3. 관리자 API

### 3.1 검수 queue

`GET /api/admin/evidence-verifications?status=MANUAL_REVIEW&riskFlag=OCR_MISMATCH&page=0&size=20`

관리자 organization은 토큰에서 결정한다. URL/query로 다른 organization을 선택하지 않는다.

### 3.2 검수 상세와 원본 열람

- `GET /api/admin/evidence-verifications/{casePublicId}`
- `POST /api/admin/evidence-verifications/{casePublicId}/files/{filePublicId}/download-url`

download URL 발급 자체를 감사 로그에 기록하고 1~5분 만료, 1회성 사용을 권장한다.

### 3.3 1차 판정 제출

`POST /api/admin/evidence-verifications/{casePublicId}/reviews`

```json
{
  "result": "MATCH",
  "assuranceLevel": "L2",
  "reasonCode": "OFFICIAL_NOTICE_AND_ORIGINAL_MATCH",
  "officialReferenceUrl": "https://allowlisted.example/official-result",
  "note": "개인정보 없는 내부 메모",
  "version": 3
}
```

서버가 URL allowlist, reviewer와 제출자의 분리, 가능한 최대 assurance를 검증한다. 2인 검수가 필요한 정책이면 첫 요청은 case를 `AWAITING_SECOND_REVIEW`로만 변경한다.

### 3.4 2차 판정

`POST /api/admin/evidence-verifications/{casePublicId}/reviews/confirm`

```json
{
  "firstReviewPublicId": "review-uuid",
  "result": "MATCH",
  "version": 4
}
```

첫 검수자와 같은 계정은 409를 반환한다. 두 판단이 다르면 자동 확정하지 않고 상위 관리자 queue로 보낸다.

### 3.5 철회·재검증

- `POST /api/admin/evidence-verifications/{casePublicId}/reverify`
- `POST /api/admin/evidence-verifications/{casePublicId}/revoke`

철회 사유, 근거, 관리자 ID가 필수다. 기존 decision을 update하지 않고 `supersedes_id`를 가진 새 decision을 만든 뒤 연결된 졸업요건을 재평가한다.

## 4. 기관 challenge와 callback

### 4.1 기관 이메일 challenge 생성

`POST /api/admin/evidence-verifications/{casePublicId}/issuer-challenges`

```json
{
  "recipient": "awards@official-university.example",
  "locale": "ko"
}
```

등록된 기관 domain만 허용한다. nonce 원문은 저장하지 않고, 링크는 짧게 만료되며 한 번만 쓸 수 있다.

### 4.2 기관 확인 화면

- `GET /api/public/issuer-challenges/{token}`: 최소 claim 조회
- `POST /api/public/issuer-challenges/{token}/confirm`: 발급 사실 확인/불일치

기관 확인 하나가 곧바로 졸업요건 확정이 되는 것은 아니다. subject와 claim 일치, token 유효성, 기관 registry를 서버가 결합해 decision을 만든다.

### 4.3 provider webhook

`POST /api/integrations/evidence-providers/{providerCode}/callbacks`

필수 header:

- `X-Provider-Timestamp`
- `X-Provider-Signature`
- `Idempotency-Key`

서명 불일치, 허용 시간 초과, 이미 소비한 callback은 거절한다. 항상 빠르게 수신한 뒤 비동기로 처리한다.

## 5. 졸업요건 연결 API 변화

비교과 입력 API는 다음처럼 검증 상태를 받지 않는다.

```json
{
  "recordType": "CERTIFICATE",
  "title": "정보처리기사",
  "numericValue": null,
  "issuedAt": "2026-06-12",
  "expiresAt": null,
  "evidenceSubmissionPublicId": "submission-uuid"
}
```

서버 동작:

1. submission owner와 학생 profile owner가 같은지 확인한다.
2. 최신 decision이 `VERIFIED`이고 아직 유효한지 확인한다.
3. 졸업 규칙 `parameters_json.minimumAssuranceLevel` 이상인지 확인한다.
4. claim type과 비교과 record type이 호환되는지 확인한다.
5. record, evidence binding, 평가 재실행 event를 한 transaction에서 만든다.

조건이 부족하면 기록은 `SELF_REPORTED`로만 만들거나 422를 반환하며, 절대로 request 값으로 `VERIFIED`를 저장하지 않는다.

## 6. 오류 코드

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | `EVIDENCE_INVALID_CLAIM` | schema/필드 오류 |
| 400 | `EVIDENCE_UNSUPPORTED_FILE` | 형식·크기 제한 위반 |
| 403 | `EVIDENCE_ACCESS_DENIED` | 본인/조직 범위 아님 |
| 409 | `EVIDENCE_VERSION_CONFLICT` | optimistic lock 충돌 |
| 409 | `EVIDENCE_ALREADY_SUBMITTED` | 불변 제출 수정 시도 |
| 409 | `EVIDENCE_REVIEWER_CONFLICT` | 자기검수/동일 2차 검수자 |
| 422 | `EVIDENCE_MALWARE_DETECTED` | 악성 파일 |
| 422 | `EVIDENCE_SUBJECT_MISMATCH` | 제출자와 증명 대상 불일치 |
| 422 | `EVIDENCE_ASSURANCE_TOO_LOW` | 졸업 정책의 최소 강도 미달 |
| 503 | `EVIDENCE_PROVIDER_UNAVAILABLE` | 기관 일시 장애; 제출을 허위로 판정하지 않음 |

## 7. 프론트 화면

1. **증빙 추가 wizard**: 유형 선택 → 구조화 정보 → 파일 업로드 → 개인정보 동의 → 제출
2. **내 증빙 목록**: 상태와 다음 행동을 표시하되 `L1`을 “검증 완료”로 표현하지 않음
3. **상세 timeline**: 제출, 자동검사, 추가 요청, 관리자 검수, 만료/철회
4. **졸업요건 화면**: 각 비교과 항목에 근거 증빙, 검증 강도, 유효기간, 부족 사유 표시
5. **관리자 queue**: 위험 flag, 공식 출처 링크, 1·2차 검수 분리, 원본 열람 감사

상태 문구 예시:

- `L1`: “파일 확인 완료 · 발급기관 확인 전”
- `INCONCLUSIVE`: “공식 확인 수단을 찾지 못했습니다 · 추가 검수 필요”
- `VERIFIED/L3`: “발급기관 확인 완료”
- `EXPIRED`: “유효기간 만료 · 졸업요건에서 제외됨”

## 8. 인수 테스트

- 다른 사용자의 submission/file public ID로 조회·다운로드 불가
- 다른 organization 관리자가 case 조회·판정 불가
- request에 `VERIFIED`를 넣어도 무시 또는 400
- 악성 파일, MIME spoof, oversize, hash 불일치 차단
- QR의 localhost/private IP/redirect/DNS rebinding 차단
- 동일 webhook과 submit 재전송 시 decision/binding 중복 없음
- provider timeout은 `REJECTED`가 아니라 retry/`INCONCLUSIVE`
- subject mismatch는 기관 응답이 참이어도 `REJECTED`
- 두 검수자가 같거나 제출자와 같으면 확정 불가
- 만료·revoke 후 비교과 상태와 최신 졸업평가가 재계산됨
- raw 문서와 개인정보가 audit log, application log, blockchain payload에 없음
