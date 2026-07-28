# 2026-07-27 Trekkey 통합 회의 안건

- 회의 목적: 업무 도메인과 Credential·Kaia 앵커링의 연결 계약 확정
- 기준 브랜치: `develop` (`9d8959d`)
- 병합 목표: 백엔드 `develop`, 프론트 `main`
- 심사관리 기능 책임자: **혁모**
- 작성자와 기능 책임자: 반드시 구분

## 0. 병합 결과

- [백엔드 보안 PR #8](https://github.com/Tok-Baro/Trekkey_BackEnd/pull/8): `main` 병합 완료 (`5213121`)
- [백엔드 통합 PR #9](https://github.com/Tok-Baro/Trekkey_BackEnd/pull/9): `develop` 병합 완료 (`9d8959d`)
- [프론트 통합 PR #1](https://github.com/Tok-Baro/Trekkey/pull/1): 최신 팀원 API 계약 불일치로 Draft 유지
- 실제 JWT·DB 비밀값 교체, ReviewRound 전환, 공유 DB migration은 후속 작업

## 1. 회의가 끝날 때 남아야 하는 결과

- [ ] 합의된 `REVIEW_ROUND` 모델의 코드 전환 범위·담당·별도 PR 확정
- [ ] 심사 마감·미제출·동점·수동 판정 규칙 확정
- [ ] 팀원 등록 API와 명단 확정 시점 확정
- [ ] 수상 확정부터 Credential 발급까지의 트랜잭션 계약 승인
- [ ] 프론트·백엔드 `publicId` 응답 계약 승인
- [ ] Kairos 배포 담당자와 필요한 비밀값 보관 위치 지정
- [ ] DB 스키마 반영 방법과 개발 DB 초기화 일정 확정
- [ ] 각 PR의 리뷰어·완료일 지정
- [ ] 기존 비밀값 교체 담당자와 완료 시각 지정

## 2. 이미 합의된 기준

| 주제 | 확정 기준 |
| --- | --- |
| 상장 단위 | 팀당 `AWARD`와 수상 Credential 각 1건 |
| 개인 이력 | 발급 당시 팀원 전원을 `ANC_CREDENTIAL_SUBJECT`에 snapshot으로 저장 |
| 팀원 정책 | 가입한 팀원은 이탈·삭제하지 않음, 백엔드 저장·조회 API 구현 |
| 팀원 변경 | 명단 확정 전 추가만 허용, 잘못 만든 팀은 반려 후 재신청 |
| 제출물 | 팀당 `SUBMISSION` 1행, 마감 전 현재 내용을 덮어씀 |
| 제출 이력 | 별도 제출 버전 테이블과 `sourceVersion`을 만들지 않음 |
| 파일 해시 | 업로드 스트림에서 SHA-256을 동기 계산 |
| 제출 확정 | 첫 심사 시작 시 `finalizedAt` 기록, 이후 수정 금지 |
| row lock | 버전 관리가 아니라 동시 파일 교체 직렬화를 위한 DB 잠금 |
| 심사 결과 | 라운드 마감 후 점수·순위·판정 수정 금지 |
| 심사 모델 | 고정 일정은 `CONTEST`, 가변 심사만 `REVIEW_ROUND`로 관리 |
| Credential 원문 | canonical JSON과 snapshot은 MySQL에 불변 저장 |
| 온체인 데이터 | 개인정보 없이 Merkle root와 상태만 저장 |
| 체인 | MVP는 Kaia Kairos, EVM 추상화는 유지 |
| 정정 | 기존 Credential 수정이 아니라 신규 발급 후 `SUPERSEDED` |
| 보장 범위 | 앵커링 전 입력의 진실성은 학교 책임, 앵커링 후 변경 여부를 검증 |

## 3. 현재 저장소 상태

| 항목 | 상태 | 조치 |
| --- | --- | --- |
| 백엔드 `develop` | PR #9까지 병합, 업무 원장·Credential·Kaia adapter·팀원 API 포함 | ReviewRound 전환과 DB migration 진행 |
| [백엔드 통합 PR #9](https://github.com/Tok-Baro/Trekkey_BackEnd/pull/9) | 2026-07-27 `develop` 병합 완료 (`9d8959d`) | 후속 작업을 별도 PR로 분리 |
| 기존 백엔드 PR #7 | 종료됨 | PR #9로 대체 완료 |
| [보안 PR #8](https://github.com/Tok-Baro/Trekkey_BackEnd/pull/8) | 2026-07-27 `main` 병합 완료 (`5213121`) | 실제 JWT·DB 비밀값 교체 |
| [프론트 통합 PR #1](https://github.com/Tok-Baro/Trekkey/pull/1) | CI 성공·mergeable, 최신 팀원 API 계약 불일치 | Draft 유지, 계약 수정과 smoke test 뒤 병합 |
| [백엔드 PR #10](https://github.com/Tok-Baro/Trekkey_BackEnd/pull/10) | 팀원 기반 참가 신청, 리뷰·CI 없이 작성자가 병합 | 사후 코드 리뷰와 프론트 계약 반영 필요 |
| `feat/review-scoring` | `2d62dcd`, PR 없음, `develop`보다 22커밋 뒤 | 통째 병합하지 않고 심사 코드만 선별 이식 |
| 백엔드 CI | Java·Solidity·TypeScript 검증 성공 | `develop` 후속 PR에도 유지 |
| 프론트 CI | Node 22 production build 검증 추가 | PR #1 결과 확인 후 병합 |
| 브랜치 보호 | Private Free 플랜에서 서버 강제 제한 | 팀 규칙과 CI로 우선 운영 |

## 4. 커밋 추적과 책임 구분

| 사실 | 작성 이력 | 기능·조치 책임 |
| --- | --- | --- |
| `application-local.properties` 최초 추적 | `3634c21`, `naeunmin` | 보안 담당자가 키 교체, 전원이 기존 값 폐기 |
| JWT 기본키 최초 도입 | `d409039`, `naeunmin` | 보안 PR #8에서 제거 |
| 제출 덮어쓰기·SHA-256 | `cbd88e6`, `ijunsu` | 제출 담당자와 통합 PR 리뷰어 |
| 심사 도메인 구현 | `5ce57cc`, `ijunsu` | **기능 승인·인수 책임자는 혁모** |
| 제출~심사위원 배정 대안 구현 | `2d62dcd`, `구혁모` | 최신 `develop` 기준으로 선별 이식·동시성 수정 |
| 팀원 기반 참가 신청 | `9ae9180`~`0ae333c`, `naeunmin` | PR #10 사후 리뷰·프론트 계약·동시성 보완 |
| 수상 도메인 구현 | `a4d23c9`, `ijunsu` | 수상 담당자 확정 필요 |
| 업무·앵커링 통합 | `011f36e`, `69d531a`, `e1df66f`, `1ad4f8c`, `ijunsu` | 통합 PR 리뷰어 공동 책임 |
| 블록체인 초안의 `main` 직접 반영 | Codex 작업 후 revert | 새 PR은 `develop` 대상으로 교체 |

책임 원칙:

- 커밋 작성자는 변경 이력을 설명한다.
- 기능 책임자는 상태 전이와 사용자 요구를 승인한다.
- 통합 담당자는 충돌 해결과 전체 회귀 테스트를 책임진다.
- 심사관리 최종 승인자는 혁모다.

## 5. P0 보안 안건

### 현재 조치

- [x] 추적된 `application-local.properties`를 현재 트리에서 제거
- [x] 비밀값 없는 `application-local.properties.example` 추가
- [x] JWT 기본 서명 키 제거
- [x] 누락·잘못된 Base64·64바이트 미만 키의 기동 거부 테스트
- [x] `.DS_Store` 추적 제거 및 ignore 추가

### 팀이 해야 할 외부 조치

- [ ] 실제 실행 환경의 `JWT_SECRET` 교체
- [ ] 저장소 값과 같은 DB 비밀번호를 다른 환경에서 썼다면 DB 비밀번호 교체
- [ ] 기존 access·refresh token 전부 무효화
- [ ] 운영 환경 `JWT_REFRESH_COOKIE_SECURE=true` 확인
- [ ] Git 과거 이력 정리 여부 결정

### 이력 정리 결정

| 선택 | 장점 | 비용 |
| --- | --- | --- |
| 키만 교체하고 이력 유지 | 협업 중단이 없음 | 과거 값은 Git 이력에 계속 보임 |
| `git filter-repo` 후 강제 push | 과거 blob까지 제거 | 모든 브랜치·PR 영향, 전원 재클론 필요 |

권장 순서:

1. 키 교체
2. 보안 PR 병합
3. 팀 작업 브랜치 정리
4. 이력 재작성 필요성 재평가

## 6. P0 업무·Credential 연결 인수

### 6.1 `TEAM_MEMBER` 저장 경로

PR #10 반영:

- 참가 신청 요청이 `memberUserIds`를 받음
- 대표자와 일반 팀원을 모두 `TEAM_MEMBER`에 저장
- `/api/participants/search?keyword=`에서 같은 학교의 활성 참가자를 이름·학번으로 검색
- `/api/me/applications`, `/api/me/teams`로 일반 팀원도 참가 이력을 조회
- 수상 Credential 발급기는 `TEAM_MEMBER` 전원을 안정된 사용자 ID 순서로 조회

통합 PR #9에서 추가로 반영:

- 팀 수정 시 기존 팀원 삭제 금지
- 요청에 포함된 새 팀원만 추가
- 기존 팀원과 새 팀원의 합계가 최대 인원을 넘으면 거부
- 제거하기로 합의한 `TEAM.sourceVersion`은 다시 도입하지 않음

남은 위험:

- 프론트 PR #1은 아직 `memberCount`와 자유 입력 명단을 보내므로 새 백엔드 요청과 호환되지 않음
- 프론트 조회 경로도 `/api/users/me/applications`에서 `/api/me/applications`로 변경 필요
- 동일 대회의 여러 팀이 같은 사용자를 동시에 추가할 때 서비스 선조회만으로는 완전한 유일성을 보장하지 못함
- 기존 개발 DB에 `TEAM`만 있고 `TEAM_MEMBER`가 없는 신청은 backfill 또는 DB 재생성이 필요
- PR #10은 리뷰·CI 없이 작성자가 직접 병합했으므로 사후 인수 리뷰가 필요

완료 조건:

- [x] 팀 생성 직후 대표자 `TEAM_MEMBER` 생성
- [x] 학번 검색으로 같은 학교 팀원 선택
- [x] 다른 학교·비활성·비참가자 추가 거부
- [x] `(teamId, userId)` 중복 금지
- [x] 팀 수정 시 기존 팀원 삭제 금지
- [x] 명단 확정 후 신청 수정 거부
- [ ] 동시 요청에서도 동일 대회 중복 참가 방지
- [ ] 프론트 팀원 검색·선택 UI와 새 요청·응답 계약 반영
- [ ] 기존 데이터 처리 방식 확정
- [ ] 대표자와 일반 팀원 모두 수상 조회·Credential subject E2E 성공

### 6.2 최종 ERD와 심사 코드 불일치

현재 확인:

- 최종 문서: `CONTEST` 고정 신청·제출 일정 + `REVIEW_ROUND` 0..N
- 실제 코드: 범용 `CONTEST_STAGE`와 `stageType`
- 제출 서비스도 `SUBMISSION` stage를 조회
- 심사 서비스도 `REVIEW/PRESENTATION` stage를 분기

논의했던 선택안:

| 안 | 장점 | 단점 |
| --- | --- | --- |
| A. 현재 `CONTEST_STAGE` 유지 | 추가 구현이 적음 | 고정 단계까지 다형화, 문서와 불일치 |
| B. `REVIEW_ROUND`로 전환 | 최종 ERD와 일치, 신청·제출 규칙 단순 | 백엔드·프론트 API 동시 수정 필요 |

기획 결론은 **B안**이다. 이번 회의에서는 모델을 다시 고르는 것이 아니라 실제 코드 전환 범위와 담당을 확정한다. 결론을 바꾸려면 업무·심사·제출·수상·프론트 담당 전원의 명시적 재합의가 필요하다.

공동 아키텍처 결정:

- [x] `REVIEW_ROUND` 백엔드 전환 책임자: **혁모**
- [ ] 혁모 PR의 리뷰어와 마감일
- [ ] 라운드 상태 `PREPARING/OPEN/FINALIZED`
- [ ] 신청 기간과 제출 마감의 `CONTEST` 컬럼 위치
- [ ] 심사 API의 `{stageId}`를 `{roundId}`로 변경
- [ ] 프론트 심사 화면의 용어를 “단계”가 아닌 “라운드”로 통일

혁모는 공동 결정 이후 ReviewRound 백엔드 전환을 구현하고, 심사 상태 전이와 인수 테스트까지 책임진다.

## 7. 심사관리 인수 안건

기능 책임자: **혁모**

### 7.1 라운드 시작

- [ ] 심사위원이 1명 이상이어야 함
- [ ] 대상 제출물이 1건 이상이어야 함
- [ ] 대상 제출물을 이 시점에 `finalizedAt`으로 확정
- [ ] 같은 라운드 시작 API 동시 호출 방지
- [ ] 전 심사위원 × 전 제출물 배정 정책을 MVP에서 유지할지 결정

### 7.2 심사 제출

- [ ] 기준별 점수 합계와 각 기준 최대점 검증
- [ ] 심사 제출 후 수정 금지
- [ ] 만료·재발급된 심사 링크 정책
- [ ] 심사위원 삭제는 배정 전까지만 허용
- [ ] 의견 필수 여부와 최대 길이 확정

### 7.3 라운드 마감

현재 코드상 주의점:

- 심사가 한 건도 없는 제출물도 0점으로 계산 가능
- `MANUAL` 라운드는 개별 판정 전에도 라운드가 완료될 수 있음
- 동점일 때 안정적인 2차 정렬 기준이 없음
- 라운드 시작·마감 row lock이 없음

회의 결정:

- [ ] 모든 배정의 심사 제출을 마감 조건으로 요구할지
- [ ] 미제출 심사위원을 제외할지 0점 처리할지
- [ ] 동점 시 공동 순위 또는 제출 시각·ID 순으로 결정할지
- [ ] 수동 판정 대상이 모두 확정된 뒤에만 라운드를 마칠지
- [ ] 마감 후 정정이 필요하면 append-only 이벤트를 둘지

혁모 인수 테스트:

- [ ] 라운드 시작 중복 호출 실패
- [ ] 제출 확정 뒤 파일 덮어쓰기 실패
- [ ] 심사 제출 뒤 재제출 실패
- [ ] 미완료 배정이 있으면 마감 실패
- [ ] 동점 결과가 재실행해도 동일
- [ ] 마감 뒤 기준·배정·점수·순위·판정 수정 실패
- [ ] 수동 판정 사유와 담당자 저장

### 7.4 혁모 구현 산출물

혁모 작업은 단순 검토나 승인으로 끝내지 않고 아래 두 PR로 나눈다.

**PR H1. ReviewRound 모델 전환**

- `CONTEST_STAGE`의 고정 `APPLY/SUBMISSION/AWARD` 개념 제거
- 신청 기간과 제출 마감을 `CONTEST` 컬럼으로 이전
- 심사 도메인을 `REVIEW_ROUND`, `REVIEW_ROUND_ENTRY`로 전환
- criterion·assignment·task·entry 외래 키와 조회를 `roundId` 기준으로 변경
- 심사 API의 `{stageId}`를 `{roundId}`로 변경
- 기존 개발 DB migration 또는 재생성 스크립트 작성
- 대회·제출 담당자는 경계 변경을 리뷰하고, 혁모가 심사 영역 구현을 책임

**PR H2. 심사 마감 무결성**

- 라운드 시작·마감 동시 호출 row lock
- 심사 시작 시 대상 제출물 확정
- 미완료 배정 마감 차단
- 확정된 동점 규칙과 안정적인 정렬 적용
- 수동 판정 전원 완료 검사
- 심사 제출·라운드 마감 이후 수정 차단
- 단위·통합 테스트와 Swagger 요청·응답 예시 갱신

혁모 완료 조건:

- H1과 H2를 별도 Draft PR로 제출
- 본인이 작성한 PR은 다른 팀원 1명 이상이 리뷰
- 심사 관리자 API와 심사위원 제출 API의 happy path·실패 path 테스트
- 프론트 담당자에게 `roundId` API 계약과 상태 enum을 인계
- 7.1~7.3 체크리스트를 직접 시연하고 인수 완료 기록

### 7.5 기존 `feat/review-scoring` 감사 결과

확인한 커밋은 `2d62dcd` 한 건이며 88개 파일, 11,929줄 추가·90줄 삭제 규모다. 최신 `develop`보다 22커밋 뒤이고 현재 코드와 21개 파일이 충돌하므로 이 커밋을 통째로 cherry-pick하거나 바로 PR로 올리지 않는다.

살릴 구현:

- 256-bit 심사 링크 토큰 생성·해시 저장·만료/폐기 검증
- 심사위원 등록과 링크 발급·재발급
- 라운드 entry 준비와 심사위원별 assignment 준비
- 심사위원 접근 확인과 심사 시트 조회
- 서비스·컨트롤러·MySQL 동시성 테스트 시나리오

가져오지 않을 구현:

- 현재 `develop`의 대회·제출·보안 코드를 덮는 중복 구현
- 합의한 단순 덮어쓰기 정책과 충돌하는 `/submit`, `/reopen`, `/withdraw` 상태 전이
- `ContestStage`, `stageId`, `REVIEW/PRESENTATION`을 계속 사용하는 API와 모델

아직 없는 구현:

- 기준별 점수 제출과 `Review`·`ReviewScoreItem` 저장
- 제출 후 재채점 금지와 라운드 마감
- 점수 집계·순위·동점·통과/탈락·수동 판정
- `PREVIOUS_PASSED` 기반 다음 라운드 대상 산출
- 목표 모델인 `ReviewRound`와 `roundId` 전환

검증 결과:

- 기본 테스트: 293개 발견, 15개 MySQL 전용 테스트 제외, 실패 0
- 실제 MySQL 8.4: 293개 실행, 1개 실패
- 실패: 같은 심사위원·라운드의 동시 배정 준비 중 한 성공 요청이 빈 목록을 반환
- 원인 후보: MySQL `REPEATABLE READ` 스냅샷에서 잠금 대기 후 일반 조회가 직전 트랜잭션의 배정을 보지 못함
- 완료 조건: 잠금으로 읽은 최신 assignment를 직접 응답하거나 current read를 사용한 뒤 해당 테스트 반복 통과

정리 순서:

1. 기존 브랜치는 삭제하지 않고 참고용으로 보존한다.
2. PR #9가 `develop`에 반영된 뒤 최신 `develop`에서 새 H1 브랜치를 만든다.
3. `2d62dcd`는 cherry-pick하지 않고 위의 “살릴 구현”만 현재 패키지·ReviewRound 모델에 맞춰 옮긴다.
4. H1은 모델·조회·심사위원·배정까지, H2는 채점·마감·불변성까지 분리한다.
5. 각 PR에서 일반 테스트와 MySQL 8 동시성 테스트를 모두 CI로 실행한다.

## 8. 제출물 인수 안건

현재 통합 PR 반영:

- `SUBMISSION`은 팀당 1행
- 재제출은 현재 제목과 파일 목록 덮어쓰기
- `sourceVersion`과 `integrityStatus` 제거
- 파일 SHA-256은 업로드 시 계산
- `TEAM` 행을 먼저 잠가 최초 제출과 덮어쓰기를 팀 단위로 직렬화
- 기존 파일은 DB 커밋 뒤 삭제
- DB 롤백 시 새 파일 삭제
- row lock은 동시 덮어쓰기 직렬화에만 사용

확인할 예외:

- [ ] 최초 동시 제출 테스트에서 1행만 생성
- [ ] 우회 쓰기의 유일 제약 오류를 도메인 409로 변환
- [ ] 파일 저장 도중 일부만 성공하면 성공한 새 객체 정리
- [ ] 삭제 실패 객체를 추적할 운영 로그·정리 작업
- [ ] 허용 확장자 외 MIME 검증 수준
- [ ] 최대 요청 크기와 프론트 제한 일치

기존 DB 반영:

- `docs/migrations/2026-07-27-remove-submission-version-state.sql`
- 신규 DB는 현재 엔티티로 생성
- 기존 개발 DB는 migration 실행 또는 재생성 필요

## 9. 수상 → Credential 계약

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 학교 관리자
    participant Award as Award Service
    participant Issue as Credential Issuance
    participant DB as MySQL
    participant Batch as Batch Admin API
    participant Signer as Issuer Signer
    participant Worker as Outbox Worker
    participant Kaia as Kaia Registry

    Admin->>Award: 수상 확정
    Award->>DB: AWARD를 CONFIRMED로 변경
    Award->>Issue: 확정 수상 발급 명령
    Issue->>DB: 팀·팀원·작품 hash 조회
    Issue->>Issue: snapshot과 sourceFingerprint 계산
    Issue->>DB: Credential·Source·Subjects 저장
    DB-->>Award: 같은 transaction commit
    Admin->>Batch: READY Credential 배치 생성
    Batch->>DB: SEALED batch와 Merkle proof 저장
    Batch->>Signer: EIP-712 승인 요청
    Signer-->>Batch: issuer signature
    Batch->>DB: SIGNED batch와 outbox 저장
    Worker->>DB: outbox claim
    Worker->>Kaia: Merkle root 전송
    Kaia-->>Worker: receipt와 event
    Worker->>DB: ANCHORED 확정
```

트랜잭션 기준:

- Credential 생성 실패 시 수상 확정도 롤백
- Kaia 전송 실패는 수상·Credential DB 확정을 롤백하지 않음
- 체인 전송은 Outbox worker가 재시도
- 같은 `sourceFingerprint` 재요청은 기존 Credential 반환

결정성 기준:

- DB `LocalDateTime`은 UTC로 해석
- 팀원은 안정적인 사용자 ID 순서로 조회
- subject snapshot 순서는 canonical 규칙으로 재정렬
- 파일 SHA-256은 접두사 없는 DB 값에서 `0x` 형식으로 변환
- 서버 시간대가 달라도 같은 입력은 같은 hash를 생성
- 기존 공유 DB에 KST wall-clock으로 저장된 `confirmed_at`이 있다면 UTC 전환 전에 데이터 기준을 확인하고 migration

프론트·백엔드 수상 계약:

| 필드 | 용도 |
| --- | --- |
| `id` | 수상 공개 ID |
| `contestPublicId` | 대회 상세 이동과 필터 |
| `teamPublicId` | 팀 상세 및 소유권 연결 |
| `teamName` | 화면 표시 전용 |
| `certificateNo` | 팀이 공유하는 상장 번호 |
| `confirmedAt` | 확정 시각 |

금지:

- 팀명으로 대회나 팀을 역매칭하지 않음
- 내부 DB PK를 API에 노출하지 않음
- 현재 팀 명단으로 과거 Credential subject를 다시 계산하지 않음
- 일반 팀원의 `/api/users/me/awards` 조회는 `TEAM_MEMBER` 저장 API가 구현된 뒤 인수 완료

## 10. Kaia 앵커링 운영 안건

### MVP 구조

- Off-chain: Credential 원문, subject snapshot, 파일 manifest, Merkle proof
- On-chain: issuer key, batch ID hash, Merkle root, schema/tree version, 폐기·대체 상태
- 네트워크: Kairos `chainId=1001`
- 쓰기 모드: `LOCAL_RELAYER`
- 기본 모드: `DISABLED`

### 키 역할

| 키 | 역할 | 보관 |
| --- | --- | --- |
| Issuer signer | 학교가 batch·상태 변경을 승인 | 학교 KMS 또는 별도 signer |
| Relayer | 승인된 트랜잭션 전송과 가스 지불 | 서버 secret/KMS |
| Contract admin | issuer key·pause·권한 관리 | 운영 multisig 권장 |

회의 결정:

- [ ] Kairos 배포 계정
- [ ] issuer signer와 relayer를 다른 키로 운영
- [ ] private key 저장 위치
- [ ] 테스트 KAIA 충전 담당자
- [ ] 컨트랙트 주소와 배포 commit 기록 위치
- [ ] worker 단일 인스턴스 운영 여부
- [ ] Mainnet 전환은 MVP 범위에서 제외할지

Kairos 완료 조건:

- 컨트랙트 배포
- issuer key 등록
- 2개 이상 Credential Merkle batch 생성
- 학교 EIP-712 승인
- relayer 전송
- receipt와 contract event 확인
- 공개 검증 API에서 `VALID` 확인
- 원문 1바이트 변경 시 hash 불일치 확인
- 잘못된 proof 거부 확인
- 폐기 또는 대체 1건 검증

## 11. 개인정보와 공개 검증

현재 원칙:

- 학번, 이름, 전공을 온체인에 올리지 않음
- 학번은 학교 내부 사용자 조회 키
- 공개 검증은 무작위 `credentialPublicId` 사용

회의 결정:

- [ ] 공개 검증 응답에서 이름 전체 공개 여부
- [ ] 전공 공개 여부
- [ ] 본인 인증 전·후 응답 범위 분리
- [ ] 공개 API rate limit
- [ ] 반복 조회와 대량 수집 감사 로그
- [ ] 인증서 QR 분실 시 대응

## 12. DB 운영 안건

현재:

- JPA `ddl-auto=update`
- Flyway/Liquibase 없음
- Credential 테이블 수가 많고 유일·체크 제약이 중요

결정:

- [ ] MVP 전 Flyway 도입 여부
- [ ] 운영 `ddl-auto=validate` 전환 시점
- [ ] 기존 개발 DB 재생성 또는 수동 migration
- [ ] MySQL 8 기준 canonical bytes·hash 컬럼 타입 확인
- [ ] `MEDIUMTEXT/MEDIUMBLOB` 실제 schema 확인
- [ ] 조직별 학번 유일 제약 migration 확인

권장:

- 통합 PR 병합 전 최소 manual SQL 검증
- 첫 공유 서버 배포 전 Flyway 도입
- 운영에서 `ddl-auto=update` 사용 금지

## 13. 프론트 통합 안건

현재 실제 백엔드 연동:

- 로그인
- 대회 목록
- 참가 신청
- 내 신청
- 내 수상

현재 미연동 또는 목업:

- 관리자 대회 관리 일부
- 제출 관리 화면
- 심사위원·라운드·점수·마감
- 수상 산출·확정
- Credential 배치·승인·검증 상태

회의 결정:

- [ ] 혁모의 심사 화면 API 인수 순서
- [ ] `stageId` 또는 `roundId` 확정 뒤 프론트 타입 반영
- [ ] 수상 목록은 `contestPublicId/teamPublicId`로 연결
- [ ] Credential 상태 `READY/BATCHED/ANCHORED` 표시 수준
- [ ] 체인 장애가 사용자 수상 이력을 가리지 않도록 UI 분리

## 14. CI와 Git 규칙

백엔드 필수 검사:

- Java 21 `./gradlew clean test --no-daemon`
- Node 22 `npm ci`
- Hardhat `npm test`
- TypeScript `npx tsc --noEmit`

프론트 필수 검사:

- Node LTS `npm ci`
- `npm run build`

팀 규칙:

- 기능 브랜치 → 백엔드 `develop`
- 백엔드 `develop` → `main`은 릴리스 PR
- 프론트 기능 브랜치 → `main`
- `develop/main` 직접 push 금지
- PR 최소 1인 승인
- 본인 작성 PR 본인 단독 병합 금지
- 충돌 해결자는 전체 테스트 재실행
- 비밀값·로컬 설정 파일 커밋 금지

## 15. 90분 회의 진행 순서

| 시간 | 안건 | 결론 |
| --- | --- | --- |
| 0~10분 | 보안 PR #8과 키 교체 | 담당자·완료 시각 |
| 10~20분 | 최종 ERD와 실제 코드 차이 | 혁모 H1 범위·마감일 |
| 20~35분 | PR #10 팀원 원장 사후 인수 | 프론트 계약·동시성·기존 DB |
| 35~55분 | 심사관리 인수 | 혁모 H2 규칙·테스트 |
| 55~65분 | 제출·수상 API 계약 | publicId·예외 |
| 65~80분 | Credential·Kairos 운영 | 배포·키 담당 |
| 80~90분 | PR·CI·일정 | 담당자·마감일 |

## 16. 담당표

| 업무 | 책임자 | 리뷰어 | 완료 조건 |
| --- | --- | --- | --- |
| ReviewRound 백엔드 전환(H1) | **혁모** | 대회·제출 담당 | 모델·API·migration 일치 |
| 심사 마감 무결성(H2) | **혁모** | 백엔드 1명 | 7절 체크·테스트 통과 |
| 심사 프론트 계약 인계 | **혁모** | 프론트 담당 | `roundId`·enum·오류 계약 확정 |
| 팀원 API 사후 인수·동시성 | **은민** | 블록체인 담당 | 중복 참가·Credential E2E |
| 팀원 검색·선택 UI/API 연동 | 프론트 담당 | **은민** | `memberUserIds`·새 경로 반영 |
| 제출 동시성·파일 정리 | 회의 지정 | 통합 담당 | commit/rollback 테스트 |
| 수상 응답 publicId | 백엔드 통합 담당 | 프론트 담당 | 이름 역매칭 제거 |
| Credential·Merkle·Kaia | 블록체인 담당 | 백엔드 1명 | Kairos E2E |
| 프론트 API 통합 | 프론트 담당 | 혁모 포함 | build·화면 검증 |
| 비밀값 교체 | 저장소 관리자 | 전원 확인 | 기존 token 폐기 |
| DB migration | 백엔드 배포 담당 | 통합 담당 | 공유 DB 기동 |

## 17. 회의 기록 양식

| 안건 | 결정 | 담당 | 기한 | PR/Issue |
| --- | --- | --- | --- | --- |
| ReviewRound 전환 범위 | H1/H2 분리 | 혁모 |  |  |
| PR #10 팀원 원장 인수 | 추가만 허용·삭제 금지 | 은민 |  | #10 후속 |
| 심사 마감 |  | 혁모 |  |  |
| 동점 규칙 |  | 혁모 |  |  |
| 미제출 심사 |  | 혁모 |  |  |
| DB migration |  |  |  |  |
| Kairos 배포 |  |  |  |  |
| 개인정보 공개 |  |  |  |  |
| PR 승인 규칙 |  |  |  |  |

## 18. 병합 승인 체크리스트

- [x] 보안 PR #8 `main` 병합
- [ ] 실제 JWT·DB 비밀값 교체
- [x] 백엔드 통합 PR #9 `develop` 병합
- [ ] 프론트 통합 PR #1 리뷰어 지정
- [x] Java 전체 테스트 통과
- [x] Solidity 12개 테스트 통과
- [x] TypeScript typecheck 통과
- [x] 프론트 production build 통과
- [x] `application-local.properties`와 `.DS_Store` 미추적 확인
- [x] `sourceVersion`·`integrityStatus` 잔존 참조 없음
- [x] 수상 응답 publicId 계약 프론트 반영
- [x] 팀원 저장·학번 검색 경로 구현
- [ ] PR #10 사후 리뷰와 동시 중복 참가 테스트
- [ ] 프론트 `memberUserIds`·`/api/me/applications` 계약 반영
- [ ] 혁모 H1 ReviewRound Draft PR
- [ ] 혁모 H2 심사 무결성 Draft PR
- [ ] 혁모 심사관리 시연·인수 완료
- [ ] Kairos 환경변수는 Git 밖에서 주입
- [x] 기존 PR #7 종료 및 대체 PR 연결
