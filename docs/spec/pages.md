# Trekkey 화면·라우트 명세

- 상태 기준일: 2026-09-08 KST
- 코드 기준: 프론트엔드 `main@946525e` + 기능 완성 working tree
- 라우트 수: 명시적 사용자 경로 33개 + `* → /` fallback
- 정본 코드: `src/router.jsx`, `src/routeConfig.js`, `src/App.jsx`
- 디자인/CSS와 33개 라우트 구성은 유지. 구형 심사 경로 redirect는 대회·token fragment를 보존하도록 수정

최신 Sui 공개/Tamper/시연 호환, 관리자 접수·judge 수정, 졸업 coverage 실행은 [후속 검증 기록](../completion-2026-09-08.md)을 우선한다. 하단 v6 브라우저 관찰은 당시 실패를 보존한 역사적 기록이다.

## 접근 역할

| 표시 | 의미 |
| --- | --- |
| `PUBLIC` | 계정 로그인 없이 접근 가능 |
| `PARTICIPANT` | 서버 참가자 세션 필요 |
| `ADMIN` | 서버 관리자 세션 필요. `ROOT_ADMIN`도 역할 계층으로 통과 |
| `ROOT_ADMIN` | 최고관리자 전용 |
| `REVIEW_LINK` | JWT 계정 대신 URL fragment에서 받은 capability token 검증 필요 |

## 전체 라우트 카탈로그

<!-- ROUTE-CATALOG-START -->
| 경로 | 화면 | 접근 | 상태 | 주요 데이터·주의사항 |
| --- | --- | --- | --- | --- |
| `/` | 관리자 대시보드 | `ADMIN` | `운영 화면` | 운영 지표와 선택 대회 현황 |
| `/contests` | 대회 관리 | `ADMIN` | `운영 화면` | 대회·단계 생성, 조회, 수정 |
| `/teams` | 참가 신청·팀 관리 | `ADMIN` | `운영 화면` | 신청 승인·보완, 참가 명단 확정 |
| `/submissions` | 제출물 접수함 | `ADMIN` | `제한 운영` | 목록·다운로드는 연결. 관리자 수동 접수 handler는 no-op |
| `/judging` | 심사 관리 | `ADMIN` | `제한 운영` | 라운드·심사위원·배정·집계 연결. 기존 심사위원 수정은 no-op |
| `/awards` | 수상 확정 | `ADMIN` | `운영 화면` | 후보 계산·상격 조정·보류·확정 |
| `/credentials` | Credential 검증 원장 | `ADMIN` | `제한 운영` | 발급 현황, batch·상태 event 승인·renew·reconcile. 운영 키 절차 게이트 필요 |
| `/evidence` | 외부 증빙 검수 | `ADMIN` | `제한 운영` | 조직별 queue와 L2 2인 수동검수 |
| `/graduation-policies` | 졸업 정책 공식 출처 | `ADMIN` | `제한 운영` | 공개 공식 URL 확인. 개인 로그인 영역은 수집하지 않음 |
| `/root` | 관리자 계정 | `ROOT_ADMIN` | `운영 화면` | 초대 발급·철회, 가입 승인·거절. 화면 내부에서 역할을 재검사 |
| `/participant` | 대회 찾기 | `PARTICIPANT` | `운영 화면` | 참가자 대회 목록·검색 |
| `/participant/applications` | 내 신청 | `PARTICIPANT` | `운영 화면` | 신청과 처리 상태 |
| `/participant/submissions` | 제출물 | `PARTICIPANT` | `운영 화면` | 팀 대표자 제출·재제출·다운로드 |
| `/participant/teams` | 팀 관리 | `PARTICIPANT` | `운영 화면` | 팀 구성과 참가자 검색 |
| `/participant/results` | 결과 | `PARTICIPANT` | `운영 화면` | 확정 수상과 Credential 링크 |
| `/participant/activity` | 활동 이력 | `PARTICIPANT` | `운영 화면` | 내 Credential과 공개 프로필 설정 |
| `/participant/evidence` | 외부 증빙 제출 | `PARTICIPANT` | `제한 운영` | 파일 bundle 제출과 검수 상태. 기관 직접 검증은 아님 |
| `/participant/graduation` | 졸업 자가점검 | `PARTICIPANT` | `제한 운영` | 일부 한성대 정책 기반 비공식 평가 |
| `/participant/profile` | 마이페이지 | `PARTICIPANT` | `제한 운영` | 학적 프로필, 성적표·활동 import, 공개 활동 설정 |
| `/signup` | 회원가입 | `PUBLIC` | `운영 화면` | 참가자 가입과 학교 검색 |
| `/signup/admin` | 관리자 가입 | `PUBLIC` | `운영 화면` | 유효한 초대 token 필요, 가입 후 승인 필요 |
| `/verify` | Credential 검증 입력 | `PUBLIC` | `운영 화면` | 공개 ID 입력 |
| `/verify/:credentialPublicId` | Credential 검증 결과 | `PUBLIC` | `운영 화면` | 발급 정보·proof·효력, PDF·ZIP, Tamper Lab 연결 |
| `/activity/:publicProfileId` | 공개 활동 프로필 | `PUBLIC` | `운영 화면` | 활성화된 UUID 링크만 조회 |
| `/tamper-lab` | Tamper Lab | `PUBLIC` | `연구 화면` | fixture 또는 `mode=live` 공개 payload로 브라우저 재계산 |
| `/evidence-report` | 정량 검증 리포트 | `PUBLIC` | `연구 화면` | 현재 브라우저의 암호 계산 벤치마크 |
| `/demo` | 5분 심사 시연 | `PUBLIC` | `내부 운영` | 발표용 흐름; 선택적으로 공개 Credential을 읽음 |
| `/pitch` | 10분 발표 웹 | `PUBLIC` | `내부 운영` | 슬라이드·타이머·Demo Theater |
| `/home` | 공개 홈 | `PUBLIC` | `제한 운영` | 게스트는 운영 DB가 아닌 로컬 fixture 사용 |
| `/login` | 로그인 | `PUBLIC` | `운영 화면` | 참가자·관리자 세션 시작 |
| `/review/:contestId` | 구형 심사 호환 경로 | `PUBLIC` | `제한 운영` | `/judge/review`로 redirect하며 contest·round를 보존하지 않음 |
| `/judge/review` | 외부 심사 화면 | `REVIEW_LINK` | `운영 화면` | fragment token을 검증한 뒤 `sessionStorage`로 이동; 응답은 no-store |
| `/contest/:contestId` | 대회 상세 | `PUBLIC`·세션별 | `제한 운영` | 참가자·관리자는 서버 데이터, 게스트는 로컬 fixture |
<!-- ROUTE-CATALOG-END -->

알 수 없는 경로는 `/`로 이동한다. fallback 자체는 사용자 기능 수에 포함하지 않는다.

## 역할·데이터 분기

### 참가자

`/participant/**` 어느 탭에 진입하더라도 공통 hook이 대회, 신청, 팀, 수상, Credential과 대표자 제출물을 함께 로드한다. 역할이 참가자가 아니면 참가자 로그인 화면을 보여준다.

### 관리자

관리자 경로는 서버 `ADMIN` 또는 `ROOT_ADMIN` 세션을 요구한다. `/root`는 내비게이션과 화면 양쪽에서 `ROOT_ADMIN`을 다시 확인한다. `evidence`, `graduation-policies`, `credentials`는 각 전용 API를 사용하며 일반 대회 overview load에서 제외된다.

### 외부 심사위원

`/judge/review`는 계정 로그인이 아니라 일회성·폐기 가능한 review capability token을 사용한다. token은 URL fragment로 받아 서버에서 검증한 후 fragment에서 제거하고 `sessionStorage`로 옮긴다. `/api/review/**`가 Spring Security상 `permitAll`이라는 이유로 익명 공개 콘텐츠로 분류하지 않는다.

### 공개 화면

`/verify/**`와 `/activity/**`는 공개 API를 사용한다. `/home`과 게스트 `/contest/**`는 현재 로컬 fixture이므로 운영 대회 공개 포털로 오해하면 안 된다. 발표·연구 화면은 운영 쓰기를 하지 않는다.

## 프론트 API host 계약

- 일반 API: `VITE_API_BASE_URL`
- 인증 API: `VITE_AUTH_API_BASE_URL`; 프로덕션 미지정 시 현재 프론트 origin
- Vercel: `/api/auth/**`만 동일 출처 rewrite로 보내 refresh cookie를 first-party로 유지
- `VITE_*` 값은 브라우저 bundle에 포함되므로 비밀값을 저장하지 않음
- 프론트는 Kaia RPC나 한성대학교 개인 로그인 영역을 직접 호출하지 않음

## 알려진 UI 게이트

1. 관리자 수동 제출 접수와 심사위원 수정은 실제 API로 연결했다. 기간·권한·중복·잠금 조건과 실패/재조회 UI를 검사한다.
2. 구형 `/review/:contestId`는 대회와 token fragment를 보존하며 query token을 fragment로 옮긴다.
3. 현재 프론트 Node 테스트는 **72/72 PASS**다. 실제 JSX handler/hook 회귀를 포함하지만 실제 React DOM 기반 전체 라우트 통합이나 33개 경로 브라우저 완주를 뜻하지 않는다.
4. 졸업요건·증빙·Credential 상태 이벤트 화면은 구현됐지만 공식 정책, 기관 직접 검증, 운영 키 절차가 완성된 것으로 표시하지 않는다.
5. Sui의 `chainId=0`은 호환 sentinel이다. 공개 UI는 명시적 Sui metadata로 네트워크·체크포인트·Registry·탐색기를 표시한다.
6. live Tamper Lab의 Sui AWARD에서 브라우저 leaf/root 재계산 PASS를 Chrome으로 확인했다. 체인 상태는 서버가 확인한 결과이며 브라우저가 RPC를 독립 호출한 것이 아니다. 오래된 고정 테스트 수를 제거하고 Kaia gas 표본 한계를 표시했다.

## 이전 v6의 검증·화면 관찰 범위 (역사적 기록)

최신 실행 상태는 [전체 통합 검증·운영 인계](../sui-full-integration-2026-09-08.md)를 따른다. 아래의 자동 검사, HTTP 결과와 실제 화면 관찰은 서로 다른 증거다.

| 구분 | 확인한 결과 | 확대하면 안 되는 주장 |
| --- | --- | --- |
| 프론트 자동 회귀 | **29/29 PASS, skip 0**. 비동기 완료 후 `event.currentTarget=null`인 form reset, `maxTeamMembers`별 안내·추가 제한·제출 검증 등을 handler/hook harness 10개로 검사 | 실제 브라우저에서 두 변경 흐름을 모두 완주했다는 주장 |
| production build | **PASS** | 운영 배포 또는 모든 API/화면 연결 성공 |
| 실제 Chrome 공개 검증 화면 | 최초 PARTICIPATION 유효 결과·PDF/ZIP 버튼을 관찰했고, 최종 v6 재시작 후 PARTICIPATION reload의 취소 상태 헤딩과 AWARD lookup의 유효 수상·상격 `대상`을 screenshot으로 확인. 기존 UI 네트워크는 `-` | WORK의 Chrome 관찰, 모든 다운로드 버튼 클릭 또는 3종 전체 화면/인증 후 브라우저 완주를 완료했다는 주장 |
| 실제 Chrome live Tamper Lab | AWARD의 `Proof 직접 재계산` 클릭 → `/tamper-lab?mode=live&credential=…`. 서버 원문 claims·leaf·ProofRoot 실제 재계산 일치. 그러나 `Kaia 공개 기록 없음`·`증거 불일치 확인 필요` 경고와 `Chain 0`, 옛 Java 629·프론트 19 테스트 카드 표시 | 실제 재계산 일치만으로 Tamper Lab 전체 PASS 또는 프론트 Sui 완전 호환으로 표현 |
| 재시작 후 독립 공개 HTTP | `2026-09-08T13:02:08Z` 재시작 이후 PARTICIPATION REVOKED·WORK/AWARD VALID, 모든 claims=true·기존 두 digest 보존 확인. PDF/ZIP 6개 모두 HTTP 200·파일 시그니처 PASS | HTTP 바이너리 검사만으로 브라우저 다운로드 UI 전체 E2E를 완료했다는 주장 |
| 재시작 후 업무 readback | `13:07:45Z~13:07:51Z` **8 checks PASS**(학생 로그인 1회+GET 7회). 동일 profile 3학점·CSV 과목 1개, VERIFIED 증빙·2 reviews·L2·파일 hash, 동일 과목 ID·MAJOR_REQUIRED·현재 기관 unit 및 공개 3종 상태 보존 | 로그인 API와 GET 재조회 성공을 인증 후 브라우저 전체 화면 검증으로 일반화 |
| 전체 라우트 브라우저 coverage | **33개 경로 전체 완주 미수행** | route 목록·Node 테스트·단일 공개 화면 성공을 전체 브라우저 E2E 성공으로 표현 |

승인된 프론트 변경은 `ExternalEvidencePanel.jsx`에서 await 전에 form element를 보존하는 수정과 `ContestApplicationForm.jsx`에서 대회의 `maxTeamMembers`를 적용하는 수정뿐이다. 기존 디자인/CSS는 유지했다. 최종 Java 760개(733 PASS/27 SKIP/실패 0)·build, Sui 동일 상태 resume 1/1 및 재시작 후 위 HTTP/화면 검증을 확인했다. 첫 WORK timestamp 반올림 오탐 실패는 이력으로 남기고 원문·서명·기존 두 거래 불변을 확인한 최종 성공과 구분한다. 기존 Kaia 수상 6개를 보존해야 하고 Tamper Lab의 Sui UI 호환도 미완료이므로 단일 Sui 운영 완전 대체 조건은 여전히 충족하지 않는다.
