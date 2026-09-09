# Trekkey HTTP API 카탈로그

- 최신 통합 코드 기준: 백엔드 `HEAD@0615984` + 2026-09-08 기능 완성 working tree
- Spring controller: **37개**
- handler mapping: **109개**
- 고유 HTTP `method + path`: **108개**
- 고유 method별: `GET 47` · `POST 41` · `PUT 6` · `PATCH 9` · `DELETE 5`

`POST /api/review/files/{fileId}/download`는 JSON과 form-urlencoded consumes handler 두 개를 사용하지만 OpenAPI의 같은 PathItem에서는 하나의 `POST` operation이므로 고유 operation은 108개다.

## 표기 규칙

| 상태 | 의미 |
| --- | --- |
| `stable` | 현재 화면 또는 실제 운영 흐름이 소비하는 API |
| `prototype` | MVP는 동작하지만 데이터·정책·외부 연동·운영 게이트가 남은 API |
| `diagnostic` | 키·batch·worker·정합성·원천 동기화 등 관리자 운영 API |
| `research` | 연구·실험 전용 API. 현재 백엔드에는 해당 operation 없음 |
| `reference` | 검증 패키지·인증서 등 다른 흐름의 참조 산출물 |

`PUBLIC`은 계정 JWT가 필요 없다는 뜻이다. `REVIEW_LINK`는 Spring Security상 `permitAll`이지만 request의 capability token을 서비스에서 검증한다. `ROOT_ADMIN`은 역할 계층상 `ADMIN` API도 호출할 수 있다.

## 구현 API 전수

<!-- API-CATALOG-START -->

### 인증·조직

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/auth/signup` | `PUBLIC` | `stable` | 일반 참가자 회원가입 |
| `POST` | `/api/auth/signup/admin` | `PUBLIC`+초대 | `stable` | 초대 정보 검증 후 관리자 가입 신청 |
| `POST` | `/api/auth/signin` | `PUBLIC` | `stable` | access JWT와 HttpOnly refresh cookie 발급 |
| `POST` | `/api/auth/refresh` | refresh cookie | `stable` | refresh rotation과 token family 검사 |
| `POST` | `/api/auth/logout` | refresh cookie | `stable` | refresh 폐기와 cookie 삭제 |
| `GET` | `/api/organizations` | `PUBLIC` | `stable` | 활성 학교 검색, 선택적 `keyword` |

### 대회

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/contests` | `ADMIN` | `stable` | 대회와 단계 설정 생성 |
| `PUT` | `/api/contests/{publicId}` | `ADMIN` | `stable` | 대회와 단계 설정 전체 저장 |
| `PATCH` | `/api/stages/{stageId}/status` | `ADMIN` | `stable` | 대회 단계 상태 전환 |
| `GET` | `/api/admin/contests` | `ADMIN` | `stable` | 관리자 대회 검색·필터·정렬·페이지 목록 |
| `GET` | `/api/admin/contests/{publicId}` | `ADMIN` | `stable` | 관리자 대회 상세 |
| `GET` | `/api/contests` | `PARTICIPANT` | `stable` | 참가자 대회 검색. 현재 보안 설정상 비로그인 공개 API가 아님 |
| `GET` | `/api/contests/{publicId}` | `PARTICIPANT` | `stable` | 참가자 대회 상세와 선택적 조회수 반영 |
| `POST` | `/api/contests/{publicId}/like` | `PARTICIPANT` | `stable` | 대회 좋아요 토글 |

### 참가 신청·팀

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/contests/{publicId}/applications` | `PARTICIPANT` | `stable` | 대회 참가 신청과 팀 구성 생성 |
| `GET` | `/api/me/applications` | `PARTICIPANT` | `stable` | 내 참가 신청 목록 |
| `GET` | `/api/me/applications/{contestPublicId}/progress` | `PARTICIPANT` | `stable` | 특정 대회 신청·승인·제출 진행 상태 |
| `PATCH` | `/api/me/applications/{contestPublicId}` | `PARTICIPANT` | `stable` | 참가 신청과 팀원 정보 갱신 |
| `GET` | `/api/participants/search` | `PARTICIPANT` | `stable` | 팀원 초대용 참가자 검색 |
| `GET` | `/api/me/teams` | `PARTICIPANT` | `stable` | 내 확정·참여 팀 목록 |
| `GET` | `/api/admin/contests/{contestPublicId}/teams` | `ADMIN` | `stable` | 대회 팀 신청 목록 |
| `PATCH` | `/api/admin/teams/{teamPublicId}/status` | `ADMIN` | `stable` | 팀 신청 승인·보완·반려 등 상태 변경 |
| `POST` | `/api/admin/teams/{teamPublicId}/finalize` | `ADMIN` | `stable` | 승인 팀 참가 명단 확정과 참여 Credential 경계 호출 |

### 작품 제출

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `PUT` | `/api/teams/{teamPublicId}/submission` | `PARTICIPANT` | `stable` | multipart 작품 제출·재제출; 재제출 시 파일 전량 교체 |
| `GET` | `/api/teams/{teamPublicId}/submission` | `PARTICIPANT` | `stable` | 내 팀 작품 제출 상세 |
| `GET` | `/api/files/{fileId}/download` | `PARTICIPANT` | `stable` | 권한 확인 후 제출 파일 다운로드 |
| `GET` | `/api/admin/contests/{contestPublicId}/submissions` | `ADMIN` | `stable` | 대회 제출물 목록 |
| `POST` | `/api/admin/contests/{contestPublicId}/teams/{teamPublicId}/submission` | `ADMIN` | `stable` | multipart 수동 신규 접수; 기관·팀·기간·잠금 검증, 중복 409, 감사 기록 |
| `GET` | `/api/admin/files/{fileId}/download` | `ADMIN` | `stable` | 관리자 제출 파일 다운로드 |

### 심사 관리

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/admin/contests/{publicId}/judges` | `ADMIN` | `stable` | 심사위원 등록 |
| `GET` | `/api/admin/contests/{publicId}/judges` | `ADMIN` | `stable` | 대회 심사위원 목록 |
| `PATCH` | `/api/admin/contests/{publicId}/judges/{judgeId}` | `ADMIN` | `stable` | 이름·역할 수정; 계정 불변, 배정 이력 409, 변경 시 기존 링크 철회 |
| `GET` | `/api/admin/contests/{publicId}/judges/progress` | `ADMIN` | `stable` | 심사위원별 배정·제출 진행률 |
| `DELETE` | `/api/admin/contests/{publicId}/judges/{judgeId}` | `ADMIN` | `stable` | 심사위원 제거 |
| `POST` | `/api/admin/contests/{publicId}/judges/{judgeId}/review-link` | `ADMIN` | `stable` | 외부 심사용 capability link 발급 |
| `DELETE` | `/api/admin/contests/{publicId}/judges/{judgeId}/review-link` | `ADMIN` | `stable` | 심사 link 폐기 |
| `POST` | `/api/admin/contests/{publicId}/review-rounds` | `ADMIN` | `stable` | 심사 라운드와 평가 기준 생성 |
| `GET` | `/api/admin/contests/{publicId}/review-rounds` | `ADMIN` | `stable` | 대회 심사 라운드 목록 |
| `GET` | `/api/admin/contests/{publicId}/review-rounds/{roundId}` | `ADMIN` | `stable` | 심사 라운드 상세 |
| `PUT` | `/api/admin/contests/{publicId}/review-rounds/{roundId}` | `ADMIN` | `stable` | draft 라운드와 기준 전체 갱신 |
| `POST` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/open` | `ADMIN` | `stable` | 심사 라운드 오픈 |
| `PATCH` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/deadline` | `ADMIN` | `stable` | 라운드 마감 연장 |
| `POST` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/entries/prepare` | `ADMIN` | `stable` | 제출물·이전 라운드 결과로 심사 대상 준비 |
| `GET` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/entries` | `ADMIN` | `stable` | 라운드 심사 대상 목록 |
| `DELETE` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/entries` | `ADMIN` | `stable` | 심사 대상과 미완료 배정 초기화 |
| `POST` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/judges/{judgeId}/assignments/prepare` | `ADMIN` | `stable` | 심사위원 평가표 배정 준비 |
| `GET` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/judges/{judgeId}/assignments` | `ADMIN` | `stable` | 심사위원 배정 목록 |
| `DELETE` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/judges/{judgeId}/assignments/{assignmentId}` | `ADMIN` | `stable` | 심사 배정 취소 |
| `POST` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/judges/{judgeId}/assignments/{assignmentId}/reassign` | `ADMIN` | `stable` | 취소·만료 배정 재배정 |
| `PATCH` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/judges/{judgeId}/assignments/{assignmentId}/due-at` | `ADMIN` | `stable` | 개별 배정 마감 시각 변경 |
| `GET` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/reviews` | `ADMIN` | `stable` | 제출된 리뷰 원장 조회 |
| `POST` | `/api/admin/contests/{publicId}/review-rounds/{roundId}/finalize` | `ADMIN` | `stable` | 점수 집계와 순위·통과 결과 확정 |
| `POST` | `/api/review/access` | `REVIEW_LINK` | `stable` | capability token 유효성 확인, no-store |
| `POST` | `/api/review/assignments` | `REVIEW_LINK` | `stable` | 심사위원 평가표와 배정 조회, no-store |
| `PUT` | `/api/review/assignments/{assignmentId}/review` | `REVIEW_LINK` | `stable` | 점수·코멘트 최종 제출 |
| `POST` | `/api/review/files/{fileId}/download/check` | `REVIEW_LINK` | `stable` | 파일 다운로드 권한 사전 확인 |
| `POST` | `/api/review/files/{fileId}/download` | `REVIEW_LINK` | `stable` | JSON body 또는 native form token으로 파일 다운로드 |

### 수상

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/admin/review-rounds/{roundId}/awards` | `ADMIN` | `stable` | 최종 라운드 결과로 수상 후보·순위 계산 |
| `GET` | `/api/admin/contests/{contestPublicId}/awards` | `ADMIN` | `stable` | 대회 수상 후보·결과 목록 |
| `PATCH` | `/api/admin/awards/{awardPublicId}` | `ADMIN` | `stable` | 후보 상태·상명 등 정보 조정 |
| `POST` | `/api/admin/contests/{contestPublicId}/awards/confirm` | `ADMIN` | `stable` | 수상 결과 최종 확정과 수상 Credential 경계 호출 |
| `GET` | `/api/me/awards` | `PARTICIPANT` | `stable` | 본인의 확정 수상 목록 |

### Credential·블록체인·공개 활동

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/admin/blockchain/issuer-keys/{keyVersion}/sync` | `ADMIN` | `diagnostic` | 조직 issuer key·signer 참조 동기화 |
| `POST` | `/api/admin/blockchain/batches` | `ADMIN` | `diagnostic` | READY Credential을 Merkle batch로 seal |
| `GET` | `/api/admin/blockchain/batches` | `ADMIN` | `diagnostic` | 조직별 앵커링 batch 목록 |
| `GET` | `/api/admin/blockchain/batches/{batchPublicId}/approval` | `ADMIN` | `diagnostic` | batch EIP-712 승인 payload·상태 조회 |
| `POST` | `/api/admin/blockchain/batches/{batchPublicId}/approval` | `ADMIN` | `diagnostic` | issuer signature 제출과 앵커링 승인 |
| `POST` | `/api/admin/blockchain/batches/{batchPublicId}/approval/renew` | `ADMIN` | `diagnostic` | 만료·실패 batch 승인 갱신 |
| `POST` | `/api/admin/blockchain/batches/{batchPublicId}/reconcile` | `ADMIN` | `diagnostic` | 온체인·로컬 batch 상태 재조정 |
| `POST` | `/api/admin/blockchain/credentials/{credentialPublicId}/status-events` | `ADMIN` | `diagnostic` | 폐기·대체 상태 event 생성 |
| `GET` | `/api/admin/blockchain/status-events` | `ADMIN` | `diagnostic` | 조직 상태 event 목록 |
| `GET` | `/api/admin/blockchain/status-events/{statusEventId}/approval` | `ADMIN` | `diagnostic` | 상태 변경 승인 payload·상태 조회 |
| `POST` | `/api/admin/blockchain/status-events/{statusEventId}/approval` | `ADMIN` | `diagnostic` | issuer signature로 상태 변경 승인 |
| `POST` | `/api/admin/blockchain/status-events/{statusEventId}/approval/renew` | `ADMIN` | `diagnostic` | 만료·실패 상태 승인 갱신 |
| `POST` | `/api/admin/blockchain/status-events/{statusEventId}/reconcile` | `ADMIN` | `diagnostic` | 온체인·로컬 상태 변경 재조정 |
| `GET` | `/api/admin/contests/{contestPublicId}/credentials` | `ADMIN` | `stable` | 대회 참여·작품·수상 Credential 발급 현황 |
| `GET` | `/api/admin/teams/{teamPublicId}/credentials` | `ADMIN` | `stable` | 팀 Credential 발급 현황 |
| `GET` | `/api/me/credentials` | `PARTICIPANT` | `stable` | 내 Credential 이력 |
| `GET` | `/api/admin/students/{studentId}/credentials` | `ADMIN` | `stable` | 자기 조직 학생의 Credential 이력 |
| `GET` | `/api/me/public-activity-profile` | `PARTICIPANT` | `stable` | 공개 활동 프로필 설정 조회 |
| `PUT` | `/api/me/public-activity-profile` | `PARTICIPANT` | `stable` | 공개 프로필 활성·비활성 |
| `POST` | `/api/me/public-activity-profile/rotate` | `PARTICIPANT` | `stable` | 공개 프로필 링크 교체 |
| `GET` | `/api/public/activity-profiles/{publicId}` | `PUBLIC` | `stable` | 활성화된 비식별 공개 활동 프로필 조회 |
| `GET` | `/api/public/credentials/{credentialPublicId}` | `PUBLIC` | `stable` | hash·Merkle proof·issuer·현재 효력 검증 |
| `GET` | `/api/public/credentials/{credentialPublicId}/package` | `PUBLIC` | `reference` | 공개 허용 요약과 proof ZIP |
| `GET` | `/api/public/credentials/{credentialPublicId}/certificate` | `PUBLIC` | `reference` | QR 검증 URL이 포함된 PDF 인증서 |

### 외부 증빙 MVP

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/me/evidence-submissions` | `PARTICIPANT` | `prototype` | multipart 증빙 bundle 제출과 L2 수동검수 case 생성 |
| `GET` | `/api/me/evidence-submissions` | `PARTICIPANT` | `prototype` | 내 증빙 제출 목록 |
| `GET` | `/api/me/evidence-submissions/{publicId}` | `PARTICIPANT` | `prototype` | 내 증빙 제출·검수 상세 |
| `GET` | `/api/me/evidence-files/{filePublicId}/download` | `PARTICIPANT` | `prototype` | 본인 증빙 원본 다운로드 |
| `GET` | `/api/admin/evidence-verifications` | `ADMIN` | `prototype` | 자기 조직 검수 queue, 선택적 `status` 필터 |
| `GET` | `/api/admin/evidence-verifications/{casePublicId}` | `ADMIN` | `prototype` | 자기 조직 검수 case 상세 |
| `POST` | `/api/admin/evidence-verifications/{casePublicId}/reviews` | `ADMIN` | `prototype` | L2 검수 의견; 서로 다른 2인의 결과로 합의 |
| `GET` | `/api/admin/evidence-verifications/files/{filePublicId}/download` | `ADMIN` | `prototype` | 조직 범위 원본 다운로드와 감사 로그 |

### 졸업요건 자가점검 MVP

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/me/graduation/profile` | `PARTICIPANT` | `prototype` | 내 학적·입학·학사조직 프로필 |
| `PUT` | `/api/me/graduation/profile` | `PARTICIPANT` | `prototype` | 학적 프로필 저장·갱신 |
| `GET` | `/api/me/graduation/academic-units` | `PARTICIPANT` | `prototype` | 소속 조직의 활성 학사조직 목록 |
| `GET` | `/api/me/graduation/courses` | `PARTICIPANT` | `prototype` | 가져온 과목 기록 목록 |
| `PATCH` | `/api/me/graduation/courses/{publicId}` | `PARTICIPANT` | `prototype` | 한 과목의 분류·학사조직 매핑 교정 |
| `POST` | `/api/me/graduation/evaluations` | `PARTICIPANT` | `prototype` | 비공식 자가점검 snapshot과 coverage 생성; 학적·정책 누락 시 전체 충족 표시 차단 |
| `POST` | `/api/me/graduation/transcript-imports` | `PARTICIPANT` | `prototype` | 성적표 PDF·CSV preview 또는 `apply=true` 반영 |
| `POST` | `/api/me/graduation/activity-imports` | `PARTICIPANT` | `prototype` | 활동 PDF·텍스트 preview 또는 `apply=true` 반영 |
| `POST` | `/api/admin/graduation/sources/sync` | `ADMIN` | `diagnostic` | 한성대 공개 공식 출처 변경 확인 |

### 최고관리자

| Method | Path | 접근 | 상태 | 기능 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/root/invitations` | `ROOT_ADMIN` | `stable` | 관리자 초대 발급 |
| `GET` | `/api/root/invitations` | `ROOT_ADMIN` | `stable` | 관리자 초대 목록 |
| `DELETE` | `/api/root/invitations/{invitationId}` | `ROOT_ADMIN` | `stable` | 미사용 초대 철회 |
| `GET` | `/api/root/admin-approvals` | `ROOT_ADMIN` | `stable` | 승인 대기 관리자 목록 |
| `PATCH` | `/api/root/admin-approvals/{userId}` | `ROOT_ADMIN` | `stable` | 관리자 가입 승인·거절 |

<!-- API-CATALOG-END -->

## Swagger/OpenAPI 현황

SpringDoc 의존성과 Swagger 설정은 존재하며 기본 운영 설정에서는 API docs와 UI가 꺼져 있다.

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- 문서 활성화 설정: `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED`
- 전역 문서 정보: `Trekkey API`, `v1`, bearer JWT security scheme
- error enum은 custom operation customizer가 HTTP status별 example로 추가

`ApiSecurityDocumentation` customizer는 실제 path·method·권한을 기준으로 보안 표기를 보정한다. 실제 `/v3/api-docs`와 보안 필터 회귀 4개가 통과했다.

1. 공개 operation은 `security: []`; 보호 API는 bearer JWT를 유지한다.
2. review는 JSON/form body capability token이 필요함을 명시한다. 가짜 token header scheme을 만들지 않는다.
3. refresh는 실제 설정명 HttpOnly cookie가 필수, logout은 선택이다. 쿠키는 Swagger에서 브라우저가 임의 설정할 수 있는 헤더가 아니다.
4. controller별 tag·전체 한국어 summary와 internal surface audience 분리는 아직 별도 작업이다.

기본 운영 공개 여부는 변경하지 않았다. 외부 공개 전 tag·summary·internal audience 정책 및 CI 연결이 필요하다. native form의 기존 `@RequestParam` 바인딩 정책도 변경하지 않았으며 token을 URL에 넣지 않도록 안내한다.

## 설계 문서와 구현의 차이

### 졸업요건 설계 전용

정책 resolve/history, bulk course, non-course CRUD, 관리자 정책 CRUD·검수·발행 API는 상세 설계에 있으나 현재 controller에 없다. 구현된 API는 위 9개가 전부다.

### 외부 증빙 목표 계약 전용

object storage initiate/complete, withdraw/supersede, 별도 2차 confirm, reverify/revoke, issuer challenge, provider callback은 목표 설계이며 현재 구현 API가 아니다. 현행 2인 합의는 같은 `POST .../reviews`를 서로 다른 관리자가 두 번 호출한다.

## 재생성·검증

```bash
NODE_PATH=/path/to/bundled/node_modules node docs/tools/extract-spring-api.cjs --ref origin/main
NODE_PATH=/path/to/bundled/node_modules node docs/tools/build-documentation-readers.cjs
TREKKEY_DOCS_BACKEND_REF=origin/main node docs/tools/verify-documentation-system.cjs
```

첫 번째 명령의 고유 operation 집합과 이 문서의 marker 구간이 정확히 같아야 한다.
