# 관리자 페이지 접속·권한 보안 방침 설계 (v2)

대상: 관리자(교직원) 콘솔 — 대회 생성/수정, 참가 신청 검토, 제출물·심사·수상 관리.
기준 코드: `feat/contest-admin` (JWT + refresh rotation, `@EnableMethodSecurity`, 조직 스코프 검증 구현 상태)
v2 변경: 관리자 프로비저닝을 **운영팀 발급 → 초대 → 가입 → 승인**의 3중 게이트로 확정, 상용 서비스 조사 결과 반영.

---

## 0. 업계 사례 요약 (설계 근거)

| 서비스/표준 | 참고한 방식 | 본 설계 반영 |
|---|---|---|
| GitHub Org | 초대는 **7일 자동 만료**(self-expiring, 남용 방지), 감사 로그 `카테고리.행위` 네이밍·**180일 보존**, 조직 2FA 강제 시 미준수자 접근 차단 | 초대 만료 7일, 감사 로그 스키마·보존 기간, (P2) MFA 미등록 관리자 기능 차단 |
| Google Workspace | **Super admin 2~4명 권장**, 일상 업무 계정과 분리, 위임 admin 역할 분리 | ROOT_ADMIN 소수 유지 원칙, ROOT/일반 ADMIN 역할 분리 |
| AWS | **root 격리**(꼭 필요한 작업에만, 일상은 별도 admin), root 사용 감사·알림 | ROOT_ADMIN 운영 원칙(§2-5), 파괴적 작업 감사 필수 |
| Slack | 초대 기본 30일(단축 가능), pending invitation 관리 화면, 초대에 승인 옵션 | 초대 목록/철회 API, 승인 게이트 |
| Stripe | 팀 전체 2FA 강제(다음 로그인 시 등록 강제), IAM 전담 역할 분리 | (P2) 관리자 MFA 강제 방식 |
| OWASP ASVS / Auth Cheat Sheet | **관리 인터페이스는 MFA 필수** 명시, 잠금은 **계정 기준** + 지수 백오프, 잠금 중에도 비밀번호 재설정 경로 유지(DoS 방지) | 잠금 정책(§3-1), MFA 로드맵 |
| NIST 800-63B (AAL2) | 절대 12시간 / 무활동 30분 재인증 | 관리자 refresh 24h + access 30분의 근거, (P2) 민감 작업 step-up 재인증 |

---

## 1. 위협 모델 (무엇을 막는가)

| # | 위협 | 현재 방어 | 갭 |
|---|---|---|---|
| T1 | 관리자 계정 탈취 (브루트포스/크리덴셜 스터핑) | BCrypt 해시 | **로그인 실패 제한 없음** |
| T2 | 토큰 탈취/재사용 | refresh rotation + 재사용 감지 시 family 전체 폐기, HttpOnly 쿠키 | access token 프론트 저장 위치 미규정 |
| T3 | 권한 상승 (무단 관리자 계정 생성) | signup이 `PARTICIPANT` 고정 | **정식 관리자 생성 경로 부재** → §2에서 해결 |
| T4 | 수평 이동 (타 학교 관리자의 조작) | 서비스 계층 조직 스코프 검증 `CONTEST_FORBIDDEN` | 신규 도메인마다 반복 — 공통 규칙화 |
| T5 | CSRF | refresh 쿠키 `SameSite=Lax` + `Path=/api/auth`, Bearer 헤더 기반 API | 안전 — 쿠키 Path 확장 금지 규칙 유지 |
| T6 | XSS로 세션 탈취 | detailHtml 서버 sanitize, refresh HttpOnly | access token localStorage 저장 금지 규칙 필요(§4-4) |
| T7 | 권한 회수 지연 (퇴직/오남용 차단) | signin/reissue에서 `ACTIVE` 검증 | **access 유효 30분간 차단 불가**(§4-3) |
| T8 | 관리자 오남용/사고 추적 불가 | 없음 | **감사 로그 부재**(§5) |
| T9 | 초대·승인 절차 우회 (초대 URL 유출, 방치된 대기 계정) | — (신규) | 초대 만료·해시 저장·승인 게이트·대기 만료(§2) |

---

## 2. 관리자 계정 프로비저닝 — 3중 게이트 (확정 설계)

**원칙: 관리자 계정은 "가입"으로 만들 수 없다. 초대 → 가입 → 승인을 모두 통과해야 활성화된다.**

```
[게이트 0] 최초 관리자(ROOT_ADMIN) — 운영팀(Trekkey)이 학교 온보딩 시 직접 생성해 전달
              │  코드 경로 없음. 시드/운영 절차로만 생성 (§2-5)
              ▼
[게이트 1] 초대   ROOT_ADMIN이 이메일 지정해 초대 발급 — 만료 7일 (GitHub 패턴)
              │  만료·사용됨·철회·이메일 불일치 → 가입 차단
              ▼
[게이트 2] 가입   초대받은 본인이 관리자 회원가입 (POST /api/auth/signup/admin)
              │  가입 직후 상태 = PENDING_APPROVAL → 로그인해도 관리자 기능 불가
              ▼
[게이트 3] 승인   ROOT_ADMIN이 가입자 신원 확인 후 승인 → ACTIVE (이때부터 권한 발효)
                 거절 → INACTIVE / 7일 내 미승인 → 자동 만료(INACTIVE)
```

### 2-1. 역할 모델 확장

```java
public enum UserRole {
    ROOT_ADMIN,   // 학교 대표 관리자: 관리자 초대·승인·비활성화 전담 (운영팀이 생성)
    ADMIN,        // 일반 관리자: 대회 운영 전반
    PARTICIPANT
}
```

- **ROOT_ADMIN ⊃ ADMIN 권한**: Spring Security `RoleHierarchy` 빈으로 `ROLE_ROOT_ADMIN > ROLE_ADMIN` 설정 → 기존 `@PreAuthorize("hasRole('ADMIN')")` 컨트롤러를 수정 없이 통과.
- ROOT_ADMIN 전용 기능(`/api/root/**`): 초대 발급/철회/목록, 가입 승인/거절, 관리자 목록/비활성화.
- **학교당 ROOT_ADMIN은 1~2명 유지** (Google super admin 2~4명 권고의 하한 — 락아웃 대비 2명까지 허용, 추가 발급은 운영팀 절차).

### 2-2. 데이터 모델

```sql
ADMIN_INVITATION
  id            BIGINT PK,
  organization_id BIGINT FK NOT NULL,
  email         VARCHAR(100) NOT NULL,        -- 초대 대상 (가입 시 일치 검증)
  token_hash    VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256, 원문 미저장 (RefreshToken과 동일 패턴)
  status        VARCHAR(20) NOT NULL,         -- ISSUED | USED | EXPIRED | REVOKED
  invited_by_user_id BIGINT FK NOT NULL,      -- 발급한 ROOT_ADMIN
  expires_at    DATETIME NOT NULL,            -- 발급 + 7일
  used_at       DATETIME NULL,
  created_at, updated_at
```

```java
// UserStatus 확장
public enum UserStatus {
    PENDING_APPROVAL,  // 관리자 가입 완료, 승인 대기 (추가)
    ACTIVE, GRADUATED, WITHDRAWN, TRANSFERRED, INACTIVE
}
```

- 거절/승인만료는 별도 상태 없이 `INACTIVE` 처리 — 사유는 감사 로그(§5)로 구분 (enum 최소 유지).
- 초대 상태 전이: `ISSUED → USED`(가입) / `→ EXPIRED`(스케줄러 또는 조회 시 lazy 판정) / `→ REVOKED`(ROOT 철회).

### 2-3. API

| Method/URL | 권한 | 동작 |
|---|---|---|
| `POST /api/root/invitations` | ROOT_ADMIN | `{ email }` → 같은 조직으로 초대 생성(만료 7일), **초대 URL은 응답에만 반환**(로그 금지). 동일 이메일의 ISSUED 초대 존재 시 409 |
| `GET /api/root/invitations` | ROOT_ADMIN | 자기 조직 초대 목록 (상태·만료 표시 — Slack pending invitations 패턴) |
| `DELETE /api/root/invitations/{id}` | ROOT_ADMIN | 철회(REVOKED) — 유출 대응 |
| `POST /api/auth/signup/admin` | permitAll | `UserSignUpReq`(organizationId 불필요 — 초대에서 결정) + `inviteToken`. 검증: 해시 일치·ISSUED·미만료·**이메일 = 초대 이메일**. 통과 시 `role=ADMIN, memberType=STAFF, status=PENDING_APPROVAL` 생성 + 초대 `USED` |
| `GET /api/root/admin-approvals` | ROOT_ADMIN | 승인 대기 관리자 목록 |
| `PATCH /api/root/admin-approvals/{userId}` | ROOT_ADMIN | `{ "approve": true }` → ACTIVE / `false` → INACTIVE. 대상이 자기 조직 & PENDING_APPROVAL이 아니면 400 |

**로그인 동작**: `PENDING_APPROVAL` 상태로 signin 시도 → 기존 `ACTIVE` 검증에 걸리되, 구분된 에러 `USER_PENDING_APPROVAL`(403, "관리자 승인 대기 중입니다")로 응답해 UX 안내 (미존재/비밀번호 오류와는 다르게 — 본인은 자기 가입 사실을 알므로 정보 노출 아님).

**승인 대기 만료**: 가입 후 **7일 내 미승인 시 자동 INACTIVE** — 방치된 대기 계정이 공격면이 되는 것 방지. 스케줄러(`@Scheduled` 일 1회) 또는 승인 목록 조회 시 lazy 처리.

### 2-4. 초대 만료기간 산정 근거

| | 값 | 근거 |
|---|---|---|
| 초대 토큰 | **7일** | GitHub 조직 초대 자동 만료 7일 (관리자급 초대는 업계 하한에 맞춤. Slack 기본 30일은 일반 멤버용) |
| 승인 대기 | **7일** | 초대와 동일 — 전체 프로비저닝이 최대 14일 내 완결되거나 소멸 |
| 재발급 | 만료·철회 후 ROOT_ADMIN이 새 초대 발급 (기존 토큰 재활성화 불가) | GitHub retry 패턴 |

### 2-5. ROOT_ADMIN 운영 원칙 (AWS root 격리 패턴)

- **최초 생성은 운영팀만**: 학교 온보딩 시 운영 시드 절차로 생성 → 초기 비밀번호를 안전 채널로 전달, **첫 로그인 시 비밀번호 변경 강제**(P1: `password_change_required` 플래그).
- ROOT_ADMIN 계정은 초대/승인/계정 관리에만 사용 권장 — **일상 대회 운영은 본인 명의의 일반 ADMIN 계정을 따로 초대**해 사용 (Google "관리 계정과 업무 계정 분리" / AWS "root는 꼭 필요한 작업만").
- ROOT_ADMIN의 모든 행위는 감사 로그 필수 기록 대상(§5).
- ROOT_ADMIN 비활성화·교체는 운영팀 절차로만 (셀프서비스 미제공 — 탈취 시 전권 상실 방지).

---

## 3. 로그인 보호 (T1 — P0)

### 3-1. 로그인 실패 잠금 (OWASP Authentication Cheat Sheet 준거)

```
USER 확장: failed_login_count INT DEFAULT 0, locked_until DATETIME NULL
```

- **계정 기준**(IP 기준 아님 — OWASP 명시) 실패 카운트. **5회 실패 → 15분 잠금** (MS 권고 10회와 고보안 3~5회 사이에서, 관리자 콘솔 특성상 보수적으로 5회).
- 성공 시 카운트 리셋. 잠금 중 시도 → `USER_ACCOUNT_LOCKED`(423). **계정 존재 여부는 비노출** — 미존재 이메일은 여전히 `USER_INVALID_CREDENTIALS`.
- 잠금이 DoS 수단이 되지 않도록: 잠금 시간은 15분 고정(영구 잠금 없음) + (P1) 비밀번호 재설정 경로는 잠금과 무관하게 동작.
- 관리자·ROOT_ADMIN 계정의 잠금 발생은 감사 로그 기록.

### 3-2. 비밀번호 정책

- 최소 길이: 참가자 8자, **관리자 10자** (`@Size` — 관리자 가입 DTO 분리로 자연 적용). 주기적 강제 변경 없음(NIST 800-63B).
- ROOT_ADMIN 초기 비밀번호는 첫 로그인 시 변경 강제(P1).

### 3-3. MFA (P2 — 관리자 대상)

- OWASP ASVS: **관리 인터페이스는 MFA 필수** 명시 → 장기 로드맵에 확정 포함.
- 방식: Stripe 패턴 — ROOT_ADMIN이 조직에 "관리자 2FA 필수" 설정 시, 미등록 관리자는 **다음 로그인에서 TOTP 등록 강제**. 미준수 시 GitHub 신정책 패턴으로 **관리 기능 접근만 차단**(계정 제거 아님).
- MVP 제외, `USER.mfa_secret` 컬럼 여지만 확보.

---

## 4. 토큰·세션 정책 (T2, T7)

### 4-1. 현행 (구현됨 — 유지)

access 30분(헤더) / refresh 14일(HttpOnly 쿠키, `Path=/api/auth`, SameSite=Lax) / rotation + 재사용 감지 시 family 폐기 / 원문 미저장(SHA-256)

### 4-2. 역할별 세션 차등 — 도입하지 않음 (결정)

- 관리자 refresh를 24시간으로 줄여 매일 재로그인시키는 안은 **운영 부담 대비 이득이 낮다고 판단해 제외** (팀 결정). 전 역할 동일: access 30분 / refresh 14일.
- access 자체가 30분으로 짧고, rotation + 재사용 감지·데이터 계층 DB 검증이 있어 세션 탈취 노출 창은 이미 제한적.
- (P2) 민감 작업 step-up 재인증(수상 확정, 관리자 비활성화 직전 비밀번호 재확인)은 유지 — 세션 길이와 무관하게 유효한 방어.

### 4-3. 권한 회수 즉시성 (P1)

`INACTIVE` 전환(거절·비활성화) 시:
1. **refresh 즉시 차단** — `revokeAllByUserId(userId)` 쿼리 추가(기존 `revokeAllByFamilyId` 패턴 확장), 상태 전환 트랜잭션에 포함.
2. access는 최대 30분 잔존 수용 — 데이터 검증이 DB 기준(§6 ③)이라 실피해 범위 제한적.
3. 30분도 허용 불가한 사안 발생 시에만 `token_version` claim 대조 도입(상시 DB 조회 비용 때문에 기본 미도입).

### 4-4. 프론트 저장 규칙 (T6)

- **access token은 JS 메모리에만. localStorage/sessionStorage 금지.** 새로고침 복원은 `POST /api/auth/refresh`(쿠키)로.
- 관리자/ROOT 라우트는 프론트 role 가드 병행 — 단 보안 경계는 항상 서버.

---

## 5. 감사 로그 (T8 — P0~P1)

업계 표준 스키마(actor / action / target / timestamp / IP — GitHub·Slack·Google 공통):

```sql
ADMIN_AUDIT_LOG
  id PK, user_id FK, organization_id FK,
  action VARCHAR(60),        -- GitHub식 `카테고리.행위` 네이밍:
                             -- contest.create / contest.update / stage.status_change
                             -- team.status_change / award.confirm
                             -- invitation.issue / invitation.revoke
                             -- admin.approve / admin.reject / admin.deactivate
                             -- auth.login_locked / auth.admin_signin
  target_type VARCHAR(30), target_id BIGINT,
  detail VARCHAR(500),       -- 요약 (예: "status: PENDING→APPROVED"). 비밀번호·토큰·초대URL 절대 미포함
  client_ip VARCHAR(45),
  created_at
```

- **보존 180일** (GitHub 조직 감사 로그 기준). 이후 배치 삭제 또는 아카이브.
- 기록 시점: Command 서비스 성공 경로에서 명시 호출(`auditLogger.log(...)`), `REQUIRES_NEW` + try-catch로 본 트랜잭션 비간섭.
- **권한 이벤트(초대·승인·거절·비활성화·role 관련)는 예외 없이 기록** — 업계 공통 필수 항목.
- 조회 `GET /api/root/audit-logs` (ROOT_ADMIN, 자기 조직): P1.

---

## 6. 인가 아키텍처 — 3계층 방어 (현행 유지 + 규칙화)

```
① URL 계층    SecurityConfig — permitAll / authenticated / hasRole 큰 구역
② 메서드 계층  @PreAuthorize("hasRole('ADMIN')") 클래스 레벨 (RoleHierarchy로 ROOT 포함)
③ 데이터 계층  서비스에서 조직 스코프 DB 재검증 (CONTEST_FORBIDDEN 패턴)
```

- 신규 도메인 공통 규칙: 관리자 쓰기 컨트롤러는 반드시 클래스 레벨 `@PreAuthorize`(이중화), 서비스는 `principal.getId()`만 받아 DB 기준 재검증, 조직 검증은 공통 헬퍼(`OrganizationAccessValidator`)로 추출(P1).

```java
// SecurityConfig 최종 목표
.requestMatchers(SWAGGER_URLS).permitAll()                     // 운영 배포 시 차단 (§7)
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers("/api/organizations/**").permitAll()
.requestMatchers("/api/review/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/contests/**").permitAll()
.requestMatchers("/api/root/**").hasRole("ROOT_ADMIN")
.requestMatchers("/api/admin/**").hasRole("ADMIN")             // RoleHierarchy로 ROOT 포함
.anyRequest().authenticated()

// RoleHierarchy 빈: ROLE_ROOT_ADMIN > ROLE_ADMIN
```

---

## 7. 운영 환경 하드닝 (배포 전 체크리스트)

| 항목 | 방침 |
|---|---|
| Swagger | 운영 `springdoc.api-docs.enabled=false` 또는 ADMIN 인증 뒤로 |
| 쿠키 | 운영 `JWT_REFRESH_COOKIE_SECURE=true` (HTTPS 전용) |
| JWT secret | 운영 `JWT_SECRET` env 필수 주입, 기본값 금지 |
| CORS | 운영 프론트 도메인만 — 와일드카드 금지 |
| HTTPS | 전 구간 강제 (HSTS 인프라 계층) |
| 로그 | 비밀번호·토큰 원문·**초대 URL** 로그 금지 — PR 리뷰 항목 |
| IP allowlist | 필수 아님 — 대학 기관망 고정 IP 고객 대상 **opt-in 기능**으로 후순위 설계 (AWS/Slack 모두 옵션 제공 패턴) |

---

## 8. 에러 코드 추가분

| enum | 코드 | HTTP | 메시지 |
|---|---|---|---|
| UserErrorResponseCode | `USER_ACCOUNT_LOCKED` | 423 | 로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요 |
| | `USER_PENDING_APPROVAL` | 403 | 관리자 승인 대기 중입니다 |
| AdminInvitationErrorResponseCode | `INVITATION_INVALID` | 400 | 유효하지 않은 초대입니다 |
| | `INVITATION_EXPIRED` | 400 | 만료된 초대입니다 |
| | `INVITATION_ALREADY_USED` | 409 | 이미 사용된 초대입니다 |
| | `INVITATION_DUPLICATED` | 409 | 이미 발급된 초대가 있습니다 |
| | `APPROVAL_TARGET_INVALID` | 400 | 승인 대상 상태가 아닙니다 |

---

## 9. 적용 로드맵

| 단계 | 항목 |
|---|---|
| **P0** (관리자 기능 오픈 전 필수) | ROOT_ADMIN role + RoleHierarchy(§2-1) · 초대→가입→승인 플로우 전체(§2-2~4) · 초대/승인 만료 7일 · 로그인 실패 잠금(§3-1) · 비밀번호 최소 길이 · 감사 로그(권한 이벤트 + 대회 운영 이벤트)(§5) · 운영 하드닝(§7) |
| **P1** | `revokeAllByUserId` 즉시 차단(§4-3) · ROOT 초기 비밀번호 변경 강제 · 비밀번호 재설정(잠금 DoS 대비) · 감사 로그 조회 API · OrganizationAccessValidator 공통화 |
| **P2** | 관리자 TOTP MFA 강제(ASVS 요구 — 장기 확정) · 민감 작업 step-up 재인증 · IP allowlist opt-in · token_version |

**P0 구현 규모**: 초대 도메인(엔티티+API 6개+검증+스케줄러) + role/status enum 확장 + RoleHierarchy + 로그인 잠금 + 감사 로그 — 기존 패턴(해시 저장·에러 enum·서비스 구조) 재사용 기준 2~3일 분량.
