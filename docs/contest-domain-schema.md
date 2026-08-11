# 대회 도메인 최소 스키마

> **주의:** 이 문서의 `CONTEST_STAGE` 기반 리뷰 설정은 이전 구조다.
> 리뷰 기준·대상·배정·채점은
> [review-domain-final-erd-alignment.md](review-domain-final-erd-alignment.md)의
> `REVIEW_ROUND` 구조를 따른다.

프론트엔드의 대회 생성/수정, 공개 상세, 참가 신청, 참가 신청 관리 화면을 기준으로 정리한 엔티티 구현용 설계입니다.

대상 테이블은 `CONTEST`, `CONTEST_STAGE`, `TEAM`, `TEAM_MEMBER`, `TEAM_STAGE_RESULT`이며, 공개 화면의 좋아요 기능을 정상적으로 지원하기 위한 보조 테이블 `CONTEST_LIKE`를 포함합니다.

코드 패키지는 대회 설정과 운영 흐름을 `domain.contest`, 참가 신청과 팀 관리를 `domain.team`으로 분리합니다. 따라서 `TEAM`, `TEAM_MEMBER`, `TEAM_STAGE_RESULT`은 `domain.team`에 두고 `CONTEST`, `CONTEST_STAGE`, `CONTEST_LIKE`은 `domain.contest`에 둡니다.

## 설계 원칙

- 프론트의 표시용 집계값은 원본 테이블에서 계산하고 중복 저장하지 않습니다.
- 기간은 화면처럼 문자열로 저장하지 않고 `LocalDateTime`으로 저장합니다.
- 대회 담당자와 참가자는 이름/학번 문자열이 아닌 `USER` FK로 식별합니다.
- 자식 엔티티에서 부모를 참조하는 단방향 `ManyToOne(fetch = LAZY)`를 기본으로 합니다.
- enum은 `EnumType.STRING`으로 저장합니다.
- 모든 테이블은 별도 언급이 없어도 `BaseEntity`의 `created_at`, `updated_at`을 가집니다.

## 1. CONTEST

대회 기본 정보와 공개 공고 내용을 저장합니다.

| 컬럼 | 타입 | Null | 설명 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | N | PK |
| `public_id` | `VARCHAR(36)` | N | 공개 URL·API용 불변 ID, unique |
| `organization_id` | `BIGINT` | N | 운영 학교/기관 FK |
| `owner_user_id` | `BIGINT` | N | 담당 관리자 `USER` FK |
| `title` | `VARCHAR(150)` | N | 대회명 |
| `department` | `VARCHAR(100)` | N | 주관 부서 |
| `status` | `VARCHAR(30)` | N | `ContestStatus` |
| `participation_type` | `VARCHAR(30)` | N | `ParticipationType` |
| `max_team_members` | `INT` | N | 대표자 포함 팀당 최대 참가 인원 |
| `award_count` | `INT` | N | 예정 시상 수, 기본값 0 |
| `poster_url` | `VARCHAR(500)` | Y | 대표 포스터 URL |
| `summary` | `VARCHAR(300)` | N | 공개 페이지 한 줄 소개 |
| `target` | `VARCHAR(300)` | N | 참가 대상 안내 |
| `application_method` | `VARCHAR(500)` | N | 접수 방법 안내 |
| `benefits` | `VARCHAR(500)` | N | 시상 및 혜택 안내 |
| `tags` | `VARCHAR(500)` | Y | 프론트 계약에 맞춘 쉼표 구분 태그 |
| `detail_html` | `LONGTEXT` | N | 공고 상세 HTML |
| `view_count` | `BIGINT` | N | 공개 상세 조회 수, 기본값 0 |

### enum

```text
ContestStatus
- PREPARING       // 준비중
- APPLICATION_OPEN // 접수중
- REVIEWING       // 심사중
- AWARDED         // 수상확정

ParticipationType
- TEAM
- INDIVIDUAL
- BOTH
```

### 제약 및 검증

- `award_count >= 0`
- `max_team_members >= 1`
- `participation_type = INDIVIDUAL`이면 `max_team_members = 1`
- `public_id` unique
- `owner_user_id`는 관리자이며 `organization_id`와 같은 기관 소속이어야 합니다. 이 규칙은 서비스에서 검증합니다.
- 대회명/주관부서/담당자 검색은 `CONTEST`와 `USER`를 조인한 조회 DTO로 처리합니다.

## 2. CONTEST_STAGE

접수, 제출, 평가 라운드와 같은 대회의 실제 진행 단계를 저장합니다. 프론트의 `applicationPeriod`, `submissionDue`, `evaluationRounds`를 이 테이블에서 조립합니다.

| 컬럼 | 타입 | Null | 설명 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | N | PK |
| `contest_id` | `BIGINT` | N | 소속 대회 FK |
| `name` | `VARCHAR(100)` | N | 단계명 |
| `stage_type` | `VARCHAR(30)` | N | `StageType` |
| `sequence_no` | `INT` | N | 대회 내 순서 |
| `status` | `VARCHAR(30)` | N | `StageStatus` |
| `starts_at` | `DATETIME` | Y | 단계 시작 시각 |
| `ends_at` | `DATETIME` | Y | 단계 종료/마감 시각 |
| `target_type` | `VARCHAR(30)` | Y | 평가 단계의 대상 선정 방식 |
| `pass_rule` | `VARCHAR(30)` | Y | 평가 단계의 통과 방식 |
| `pass_count` | `INT` | Y | 상위 N팀 통과 시 팀 수 |
| `min_score` | `DECIMAL(10,2)` | Y | 기준 점수 통과 시 최소 점수 |

### enum

```text
StageType
- APPLICATION
- SUBMISSION
- REVIEW
- PRESENTATION
- AWARD

StageStatus
- PREPARING
- OPEN
- COMPLETED

StageTargetType
- ALL_SUBMISSIONS
- PREVIOUS_PASSED
- MANUAL

StagePassRule
- TOP_N
- MIN_SCORE
- MANUAL
- FINAL
```

### 제약 및 검증

- `(contest_id, sequence_no)` 유니크
- `sequence_no >= 1`
- 시작과 종료가 모두 있으면 `starts_at < ends_at`
- `target_type`, `pass_rule`, `pass_count`, `min_score`는 평가 성격의 단계에서만 사용합니다.
- `TOP_N`이면 `pass_count > 0`, `MIN_SCORE`이면 `min_score`가 필요합니다.
- 평가 기준 목록은 리뷰 담당 테이블인 `REVIEW_CRITERION`이 이 단계의 ID를 참조합니다.

### 프론트 응답 조립

- `applicationPeriod`: `APPLICATION` 단계의 `starts_at ~ ends_at`
- `submissionDue`: `SUBMISSION` 단계의 `ends_at`
- `evaluationRounds`: `REVIEW`/`PRESENTATION` 단계와 `REVIEW_CRITERION`을 조인하고 `sequence_no` 순으로 응답

## 3. TEAM

현재 프론트에서는 팀 구성원 개별 정보가 아니라 참가 인원 수만 입력하므로 `member_count`가 필요합니다.

| 컬럼 | 타입 | Null | 설명 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | N | PK |
| `contest_id` | `BIGINT` | N | 신청 대회 FK |
| `leader_user_id` | `BIGINT` | N | 대표 참가자 `USER` FK |
| `name` | `VARCHAR(100)` | N | 팀명 또는 개인 참가자명 |
| `leader_name` | `VARCHAR(100)` | N | 신청 화면에서 입력한 대표자명 스냅샷 |
| `major` | `VARCHAR(100)` | N | 신청 당시 소속/전공 스냅샷 |
| `member_count` | `INT` | N | 현재 프론트가 입력하는 참가 인원 수 |
| `status` | `VARCHAR(30)` | N | `TeamStatus` |
| `contact_email` | `VARCHAR(255)` | N | 신청 연락 이메일 |
| `phone` | `VARCHAR(30)` | N | 신청 연락처 |
| `motivation` | `TEXT` | N | 지원 동기 |

### enum

```text
TeamStatus
- PENDING          // 검토중
- APPROVED         // 승인
- REVISION_REQUESTED // 보완요청
```

### 제약 및 검증

- `(contest_id, leader_user_id)` 유니크: 대표자의 동일 대회 중복 신청 방지
- `member_count >= 1`
- 개인전은 `member_count = 1`
- 현재 프론트 정책에 맞춰 팀전/개인·팀 대회는 `member_count <= 5`
- 보완 요청 상태에서 참가자가 수정하면 `PENDING`으로 되돌립니다.

## 4. TEAM_MEMBER

팀과 가입된 사용자의 연결입니다. 현재 프론트에는 팀원 상세 입력 UI가 없으므로 지금은 필요한 관계 필드만 둡니다.

| 컬럼 | 타입 | Null | 설명 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | N | PK |
| `team_id` | `BIGINT` | N | 소속 팀 FK |
| `user_id` | `BIGINT` | N | 팀원 `USER` FK |
| `role` | `VARCHAR(20)` | N | `TeamMemberRole` |

### enum

```text
TeamMemberRole
- LEADER
- MEMBER
```

### 제약 및 검증

- `(team_id, user_id)` 유니크
- 팀에는 `LEADER`가 정확히 한 명이어야 합니다.
- `TEAM.leader_user_id`와 `LEADER` 역할의 `TEAM_MEMBER.user_id`는 같아야 합니다.
- 동일 사용자가 같은 대회의 여러 팀에 참가하지 못하게 서비스에서 검사합니다.
- 이름, 이메일, 학번, 전공은 `USER`에서 조회하며 이 테이블에 중복 저장하지 않습니다.

## 5. TEAM_STAGE_RESULT

각 평가 단계가 끝났을 때 팀의 점수, 순위, 통과 여부를 확정해 저장합니다.

| 컬럼 | 타입 | Null | 설명 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | N | PK |
| `team_id` | `BIGINT` | N | 대상 팀 FK |
| `contest_stage_id` | `BIGINT` | N | 대상 단계 FK |
| `status` | `VARCHAR(30)` | N | `TeamStageResultStatus` |
| `total_score` | `DECIMAL(10,2)` | Y | 확정 총점 |
| `rank_no` | `INT` | Y | 확정 순위 |
| `decided_at` | `DATETIME` | Y | 결과 확정 시각 |

### enum

```text
TeamStageResultStatus
- PENDING
- PASSED
- FAILED
```

### 제약 및 검증

- `(team_id, contest_stage_id)` 유니크
- `rank_no >= 1`
- 팀의 대회와 단계의 대회가 같아야 합니다.
- 심사 점수 계산 중에는 `PENDING`, 결과 확정 시 `PASSED` 또는 `FAILED`와 `decided_at`을 함께 기록합니다.

## 6. CONTEST_LIKE

공개 상세의 좋아요 토글과 사용자별 중복 방지를 위한 최소 보조 테이블입니다.

| 컬럼 | 타입 | Null | 설명 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | N | PK |
| `contest_id` | `BIGINT` | N | 대회 FK |
| `user_id` | `BIGINT` | N | 좋아요를 누른 참가자 FK |

### 제약

- `(contest_id, user_id)` 유니크
- 좋아요 수는 `COUNT(*)`로 계산하므로 `CONTEST.like_count`는 두지 않습니다.

## 저장하지 않고 계산할 프론트 필드

| 프론트 필드 | 처리 방식 |
| --- | --- |
| `owner` | `owner_user_id -> USER.name` |
| `applicationPeriod` | `APPLICATION` 단계 시작/종료 시각 포맷팅 |
| `submissionDue` | `SUBMISSION` 단계 종료 시각 포맷팅 |
| `evaluationRounds` | 평가 단계와 평가 기준을 조인해 배열로 응답 |
| `teams` | 대회별 `TEAM COUNT` |
| `submissions` | 제출 담당 테이블 `SUBMISSION COUNT` |
| `judges` | 리뷰 담당 테이블 `CONTEST_JUDGE COUNT` |
| `progress` | 현재 대회/단계 상태와 처리율로 계산 |
| `likes`, `likedBy` | `CONTEST_LIKE` 조회/집계 |
| `submitted` | 해당 팀의 `SUBMISSION EXISTS` |
| `applicantId` | `leader_user_id -> USER.student_id` |
| 팀원 이름/이메일/학번/전공 | `TEAM_MEMBER -> USER` 조인 |

## 제외한 필드

- `source_url`: 목업 시드 데이터 출처일 뿐 생성/수정 화면에서 사용하지 않습니다.
- `due_at`: `ends_at`과 의미가 겹치므로 별도 컬럼을 두지 않습니다.
- `description`: 현재 단계 생성 UI에 입력 항목이 없습니다.
- `submitted_at` on `TEAM`: 제출 담당 테이블에서 관리합니다.
- `privacy_agreed`: 현재 참가 신청 화면에 동의 입력이 없습니다. 실제 동의 기능이 추가될 때 동의 버전/시각과 함께 별도 설계합니다.
- `viewed_by`: 조회자 배열을 한 컬럼에 저장하지 않습니다. 현재 최소안은 `view_count`만 증가시킵니다.

## 현재 프론트의 확인 필요 사항

1. 참가 신청은 팀원 수만 받고 팀원 계정/학번을 받지 않습니다. 따라서 지금 상태로는 `TEAM_MEMBER`에 대표자 외 팀원을 생성할 수 없습니다.
2. 접수 기간과 제출 마감 입력이 일반 문자열입니다. API 연동 시 `datetime-local` 입력 또는 ISO-8601 변환이 필요합니다.
3. 평가 기준은 대회 폼 안에서 함께 입력하지만 백엔드에서는 리뷰 담당의 `REVIEW_CRITERION`으로 저장해야 하므로 대회 생성 API의 트랜잭션 경계를 팀과 합의해야 합니다.
4. 담당자명이 자유 입력입니다. 백엔드에서는 같은 기관의 관리자 계정을 선택해 `ownerUserId`를 보내도록 변경하는 것이 안전합니다.
