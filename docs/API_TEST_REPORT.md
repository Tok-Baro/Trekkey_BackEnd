# API 실기동 테스트 결과 보고서

- 일시: 2026-07-21 / 브랜치: `codex/contest-admin-backend`
- 환경: 로컬 부팅(`bootRun`, local 프로파일) + 테스트 전용 MySQL 8 (Docker `trekkey-test-mysql`, 포트 3307 — 로컬 MySQL 미간섭)
- 방식: curl 기반 HTTP 요청으로 전체 시나리오 실행 (Postman 컬렉션과 동일한 요청 구성)
- 시드: 학교(한성대학교) SQL insert + ROOT_ADMIN 부트스트랩(참가자 가입 후 role 승격 — 운영 절차 시뮬레이션)

## 결과 요약

**28개 시나리오 중 27개 즉시 통과, 1건 버그 발견 → 수정 → 재검증 통과.**
단위/컨트롤러 테스트 회귀: **109개 전부 통과** (skip 4는 환경변수 조건부 MySQL 통합 테스트).

## 발견·수정한 버그 1건

| 항목 | 내용 |
|---|---|
| 증상 | 참가자 토큰으로 `POST /api/contests` 호출 시 **403이 아닌 500** 반환 |
| 원인 | `@PreAuthorize`(메서드 시큐리티)의 `AuthorizationDeniedException`은 시큐리티 필터가 아니라 DispatcherServlet 내부에서 발생 → `GlobalExceptionHandler`의 `Exception` catch-all이 500으로 삼킴 |
| 수정 | `GlobalExceptionHandler`에 `AccessDeniedException` 핸들러 추가 — `JwtAccessDeniedHandler`와 동일한 `GLOBAL_403` 포맷으로 응답 |
| 재검증 | 참가자 → 대회 생성 시 `403 GLOBAL_403` 정상 응답, 회귀 테스트 전체 통과 |

## 상세 결과

### A. 인증 기본
| # | 시나리오 | 기대 | 결과 |
|---|---|---|---|
| 1 | 학교 검색 `GET /api/organizations?keyword=한성` | 200 | ✅ 200 |
| 2 | 참가자 회원가입 `POST /api/auth/signup` | 201 | ✅ 201 "회원가입성공" |
| 3 | ROOT 로그인 `POST /api/auth/signin` | 200 + accessToken + refresh 쿠키 | ✅ (HttpOnly 쿠키 확인) |

### B. 관리자 프로비저닝 (초대 → 가입 → 승인, 3중 게이트)
| # | 시나리오 | 기대 | 결과 |
|---|---|---|---|
| 4 | ROOT 초대 발급 `POST /api/root/invitations` | 201 + inviteUrl | ✅ `{front}/signup/admin?token=UUID` |
| 5 | 동일 이메일 중복 초대 | 409 | ✅ `INVITATION_DUPLICATED` |
| 6 | 무효 토큰으로 가입 | 400 | ✅ `INVITATION_INVALID` |
| 7 | 초대 이메일 불일치 가입 | 400 | ✅ `INVITATION_INVALID` (사유 비노출 확인) |
| 7b | 관리자 비밀번호 10자 미만 | 400 | ✅ `GLOBAL_400_BODY` field=password |
| 8 | 정상 관리자 가입 `POST /api/auth/signup/admin` | 201 | ✅ "승인 후 이용할 수 있습니다" |
| 9 | 사용된 초대 재사용 | 409 | ✅ `INVITATION_ALREADY_USED` |
| 10 | **승인 전 로그인** | 403 | ✅ `USER_PENDING_APPROVAL` |
| 11 | 승인 대기 목록 `GET /api/root/admin-approvals` | 200, 1건 | ✅ |
| 12 | 승인 `PATCH /api/root/admin-approvals/{id}` | 200 | ✅ "관리자 가입을 승인했습니다" |
| 13 | **승인 후 로그인** | 200, role=ADMIN | ✅ |

### C. 권한 경계
| # | 시나리오 | 기대 | 결과 |
|---|---|---|---|
| 14 | 참가자 토큰 → 대회 생성 | 403 | ⚠️ 500 → **수정 후 ✅ 403** |
| 15 | 무토큰 → 대회 생성 | 401 | ✅ `GLOBAL_401` |
| 16 | ADMIN 토큰 → `/api/root/**` | 403 | ✅ (ROOT 전용 차단) |
| 17 | **ROOT 토큰 → 대회 생성** | 201 | ✅ (RoleHierarchy: ROOT⊃ADMIN 동작 확인) |

### D. 대회 관리 (관리자 페이지 API)
| # | 시나리오 | 기대 | 결과 |
|---|---|---|---|
| 18 | 대회 생성 (단계 3개, XSS 포함 detailHtml) | 201 | ✅ |
| — | XSS sanitize | script·onclick 제거 | ✅ `<script>alert(1)</script>` 삭제, `onclick` 속성 제거, 허용 태그 보존 |
| — | 단계 sequenceNo 재정렬 | 요청 5,2,3 → 저장 1,2,3 | ✅ (APPLY→SUBMISSION→REVIEW 순 정렬) |
| — | 기간 유도 필드 | APPLY start/end, SUBMISSION due | ✅ applicationStartAt/submissionDueAt 정확 |
| 19 | 비로그인 목록 `GET /api/contests?status=OPEN&keyword=AI` | 200 | ✅ 필터·페이징 동작 |
| 20 | 비로그인 상세 (stages+criteria 포함) | 200 | ✅ |
| 21 | 수정 `PUT` (단계 이름변경+삭제+신규추가) | 200 | ✅ 기존 id 유지 수정 / 미포함 삭제 / 신규 insert 확인 |
| 22 | 단계 상태 `PATCH /api/stages/{id}/status` | 200 | ✅ WAITING→OPEN |
| 23 | 없는 대회 조회 | 404 | ✅ `CONTEST_NOT_FOUND` |
| 24 | title 누락 생성 | 400 | ✅ `GLOBAL_400_BODY` field=title |

### E. 로그인 잠금 (브루트포스 방어)
| # | 시나리오 | 기대 | 결과 |
|---|---|---|---|
| 25 | 틀린 비밀번호 5회 연속 | 각각 401 (존재 비노출) | ✅ 5회 모두 `USER_INVALID_CREDENTIALS` |
| — | 잠금 후 **정답** 비밀번호 로그인 | 423 | ✅ `USER_ACCOUNT_LOCKED` (15분 잠금 동작) |
| — | 잠금 이벤트 감사 기록 | auth.login_locked | ✅ |

### F. 토큰 수명주기
| # | 시나리오 | 기대 | 결과 |
|---|---|---|---|
| 26 | `POST /api/auth/refresh` (쿠키) | 200 + 새 쿠키(rotation) | ✅ 쿠키 값 교체 확인 |
| 27 | **폐기된 구 refresh 재사용** | 401 | ✅ `USER_INVALID_TOKEN` |
| 27b | 재사용 감지 후 신 토큰도 거부 (family 전체 폐기) | 401 | ✅ 탈취 대응 동작 확인 |

### G. 감사 로그 (DB 실측)
```
id  action              target        detail                          client_ip
1   invitation.issue    INVITATION/1  prof.kim@hansung.ac.kr          ::1
2   admin.signup        USER/3        prof.kim@hansung.ac.kr          ::1
3   admin.approve       USER/3        status: PENDING_APPROVAL→ACTIVE ::1
4   contest.create      CONTEST/1     2026 AI 아이디어 공모전          ::1
5   contest.update      CONTEST/1     2026 AI 공모전(수정)             ::1
6   auth.login_locked   USER/4        연속 5회 실패 잠금               ::1
```
✅ 전 이벤트 기록, 초대 URL·토큰 미포함, client IP 수집 확인.

## 테스트 환경 정리 방법

```bash
# 앱 종료
pkill -f "com.api.trekkey.TrekkeyApplication"
# 테스트 MySQL 컨테이너 제거
docker rm -f trekkey-test-mysql
```

## 잔여 참고 사항

1. `GlobalExceptionHandler` 403 수정분은 **미커밋 상태** (커밋 승인 대기)
2. `application-local.properties`의 로컬 MySQL 비밀번호가 실제 로컬 MySQL과 불일치 — 각자 로컬 환경값으로 맞출 것 (이번 테스트는 Docker 3307로 우회)
3. ROOT_ADMIN 부트스트랩은 "참가자 가입 → SQL role 승격"으로 수행 — 운영 온보딩 절차 문서화 필요 (설계 §2-5)
