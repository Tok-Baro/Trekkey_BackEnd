# 리뷰 도메인 최종 ERD 반영 및 DB 이전 안내

## 기준 구조

리뷰 도메인은 최종 ERD의 다음 흐름을 기준으로 한다.

```text
CONTEST
  └─ REVIEW_ROUND
       ├─ REVIEW_CRITERION
       └─ REVIEW_ROUND_ENTRY
            └─ REVIEW_ASSIGNMENT
                 └─ REVIEW
                      └─ REVIEW_SCORE_ITEM
```

- `CONTEST_STAGE`는 신청·제출 등 기존 기능이 아직 참조하므로 당장은
  유지한다.
- 새 `REVIEW`·`PRESENTATION` 성격의 `CONTEST_STAGE` 생성은 막고,
  심사 설정은 반드시 `REVIEW_ROUND` API로 생성한다.
- 심사 라운드의 설정, 상태, 기간은 `REVIEW_ROUND`가 관리한다.
- 평가 기준과 심사 대상은 각각 `review_round_id`로 라운드를 참조한다.
- 심사위원은 대회에 등록하고, 실제 채점 대상은
  `REVIEW_ASSIGNMENT`로 연결한다.

## 기존 DB에 필요한 이전

현재 프로젝트는 Hibernate의 `ddl-auto=update`를 사용한다. 이 설정은
컬럼 이름 변경과 기존 데이터의 FK 재연결을 안전하게 수행하지 못한다.
따라서 데이터가 들어 있는 DB에 배포할 때는 아래 작업을 별도
마이그레이션으로 먼저 수행해야 한다.

`origin/develop` 스키마에서 시작하는 공유 개발 DB용 전환 초안은
[`docs/migrations/2026-07-28-review-round-transition.sql`](migrations/2026-07-28-review-round-transition.sql)에
있다. 백업과 쓰기 중단 후 실행한다. 스크립트는 빈 `review_round`
껍데기를 만든 다음 기존 데이터 사전 검사를 실행한다. 검사에 실패하면
MySQL DDL의 implicit commit 때문에 생성된 빈 테이블이 남을 수 있으므로,
원인을 수정한 뒤 재실행하거나 백업으로 복구한다. 연속된 라운드 번호를
만들기 위해 window function을 사용하므로 MySQL 8.0 이상에서 실행한다.

1. `review_round` 테이블을 생성한다.
2. 시간 구간, 판정 규칙과 규칙별 옵션, 라운드 생명주기, 확정 팀,
   수상 근거, 내부 심사위원 중복을 기존 테이블에서 먼저 검사한다.
3. 기존 `contest_stage` 중 심사 용도의 행을 `review_round`로 옮긴다.
4. 기존 값은 다음 기준으로 변환한다.

   | 기존 값 | 최종 ERD 값 |
   | --- | --- |
   | `sequence_no`, `id` 순서 | 대회별로 다시 매긴 `round_no` 1..N |
   | `PREPARING` | `PREPARING` |
   | `OPEN` | `OPEN` |
   | `COMPLETED` | `FINALIZED` |
   | `ALL_SUBMISSIONS` | `ALL_SUBMISSIONS` |
   | `target_type = NULL` | `ALL_SUBMISSIONS` |
   | `PREVIOUS_PASSED` | `PREVIOUS_SELECTED` |
   | `pass_count` | `select_count` |

   기존 서비스도 대상 유형이 비어 있으면 전체 제출물을 선택했으므로,
   `target_type = NULL`만 같은 의미로 명시적으로 정규화한다.
   기존 `sequence_no`에는 신청·제출 단계의 번호도 섞여 있으므로 값을
   그대로 복사하지 않는다. 대회별 `REVIEW/PRESENTATION` 단계만
   `sequence_no`, `id` 오름차순으로 정렬해 연속된 번호를 다시 부여한다.

5. `review_criterion.review_round_id`를 nullable로 먼저 추가하고,
   기존 `contest_stage_id`를 이용해 값을 채운다.
6. 기존 `contest_stage_entry`를 `review_round_entry`로 복사하고
   `PASSED/FAILED`를 `SELECTED/NOT_SELECTED`로 변환한다.
7. `review_assignment`과 `award`의 새 `review_round_entry_id`를
   기존 entry ID로 채운다.
8. 모든 nullable 백필, 대회별 `round_no`가 1..N으로 이어지는지,
   새 유니크 키의 중복 여부를 다시 검사한다. 이 검사가 통과한 뒤에만
   새 FK를 `NOT NULL`로 변경하고 최종 유니크 제약을 생성한다.
9. 점수 컬럼을 `DECIMAL(12, 2)`로 넓히고, 심사위원 이름·역할을
   `VARCHAR(100)`으로 넓힌다. 같은 대회에서 연결 사용자가 같은
   내부 심사위원은 한 명만 존재하도록 유니크 제약을 추가한다.
10. 기존 UUID 심사 링크는 새 43자 Base64URL 토큰과 호환되지 않으므로
    모두 `REVOKED` 상태로 이전한다. 관리자는 배포 후 링크를 다시
    발급해 심사위원에게 전달해야 한다.
11. 애플리케이션 전환과 데이터 검증이 끝난 후에만 예전 리뷰 FK 컬럼과
    제약을 제거한다.

다음 데이터는 자동 변환하지 말고 배포 전에 정책을 정해야 한다.

- 시작·종료 시각이 비어 있는 기존 심사 단계:
  `REVIEW_ROUND`는 두 값이 모두 필요하다.
- `pass_rule`이 비어 있거나 `FINAL`인 기존 심사 단계:
  기존 서비스는 둘 다 최종 라운드처럼 취급했지만 최종 ERD에는 같은
  값이 없다. `TOP_N`, `MIN_SCORE`, `MANUAL` 중 실제 운영 의미에 맞는
  값으로 먼저 결정해야 한다.
- 규칙별 옵션이 맞지 않는 기존 심사 단계:
  `TOP_N`은 양수인 `pass_count`, `MIN_SCORE`는 0 이상인 `min_score`,
  `MANUAL`은 두 옵션이 모두 비어 있어야 한다.
- `OPEN`인데 유효한 기준·대상·배정이 없거나 대상이 `IN_REVIEW`가
  아닌 단계, 완료되지 않은 배정이나 판정 결과를 가진 `COMPLETED`
  단계:
  새 생명주기로 안전하게 이어갈 수 있도록 데이터를 먼저 정정해야 한다.
- 이미 `COMPLETED`이고 `target_type = MANUAL`,
  `pass_rule = MANUAL`인 단계는 각 수동 판정 결과가 완결돼 있다면
  평가 기준이나 배정이 없어도 이전할 수 있다. 단, 배정이 실제로
  남아 있다면 완료 상태와 `REVIEW`가 일치해야 하며 `final_score`는
  반드시 null이어야 한다.
- `OPEN` 또는 `COMPLETED` 심사 단계에 포함된 팀의
  `participation_finalized_at`이 비어 있으면 명단을 확인하고 먼저
  확정해야 한다.
- 기존 AWARD는 해당 대회의 마지막 심사 단계 ENTRY를 근거로 해야
  하며 ENTRY에서 도달한 팀과 `award.team_id`가 같아야 한다.
  `AWARDED` 대회에는 `confirmed_at`이 있는 `CONFIRMED` AWARD가
  적어도 하나 있어야 한다.
- 같은 대회와 사용자 조합으로 중복 등록된 내부 심사위원:
  하나의 심사위원 원장으로 합친 뒤 마이그레이션해야 한다. `user_id`가
  없는 외부 심사위원 여러 명은 허용된다.
- `COMPLETED` 행의 `finalized_at`:
  스크립트는 `updated_at`, `created_at` 순으로 대체하고 둘 다 없으면
  중단한다. 실제 확정 시각을 알 수 있으면 실행 전에 그 값으로
  보정한다.

개발용 DB처럼 기존 데이터를 보존할 필요가 없다면 백업 여부를 확인한
뒤 DB를 새로 만들어 최종 스키마를 생성할 수 있다. 운영 또는 공유
DB에서는 재생성하지 말고 위 순서의 명시적 마이그레이션을 사용한다.

현재 GitHub Actions의 `mysql-integration` 작업은 최종 엔티티로
`create-drop`한 DB에서 서비스 생명주기와 잠금 동작을 검증하고,
최소 `origin/develop` 형태의 fixture에는 위 SQL을 실제로 순방향
실행한다. 다만 fixture는 운영 전체 스키마와 데이터를 복제하지
않는다. 공유 DB에 적용하기 전에는 운영 데이터 사본 또는
스테이징에서도 SQL 전체를 리허설하고, 마지막 검증 쿼리의 결과가
모두 0인지 확인해야 한다.

옛 FK 컬럼을 한 배포 동안 남기는 것은 전환 직후 데이터 확인을 위한
것이다. 새 애플리케이션에서 Review Round 관련 쓰기가 한 건이라도
발생하면 옛 코드가 새 원장을 읽을 수 없으므로 단순 애플리케이션
롤백은 지원되지 않는다. 이 시점 이후 되돌리려면 백업 복구 또는
검증된 down-migration이 필요하다.

## 현재 구현 범위

- 라운드 및 평가 기준 생성·조회·수정
- 심사 대상 준비
  - `ALL_SUBMISSIONS`: 승인된 팀의 제출 완료 작품 전체
  - `PREVIOUS_SELECTED`: 바로 이전 확정 라운드의 선정 작품
  - `MANUAL`: 관리자가 명시한 제출물
- `PREPARING`에서 `ELIGIBLE` 대상과 배정을 초안으로 만들고,
  `OPEN` 시 현재 제출 집합과 대상 팀의 명단 확정을 다시 검증해
  제출물과 대상을 확정
- 낮은 번호의 라운드가 모두 `FINALIZED`된 뒤에만 다음 라운드를
  시작하며, 한 대회에는 `OPEN` 라운드를 하나만 허용
- 종료된 `OPEN` 라운드는 관리자 전용 종료 시각 연장 API로만 미래
  시각까지 연장하며, 배정 마감도 새 라운드 종료 시각 안에서 갱신
- 변경된 전체 제출 집합 재동기화와, 채점 이력이 없는 준비 단계의
  대상·미완료 배정 초기화
- 심사위원 등록·삭제와 링크 발급·폐기
- 심사위원 배정, 취소, 재배정, 마감 시각 변경과 진행 현황 조회
- 링크 기반 평가표 및 배정된 제출 파일 조회·다운로드
- 항목별 점수 검증, 총점 계산, 중복 제출 방지
- 라운드 시작 시 대상 제출물 확정
- 완료된 심사위원 점수의 평균 집계
- 점수 내림차순으로 경기식 공동 순위(`1, 2, 2, 4`)를 산출하고,
  동점 그룹 안에서는 entry ID 오름차순으로 응답 순서만 고정
- `TOP_N`, `MIN_SCORE`, `MANUAL` 판정과 라운드 원자적 확정
- `targetType=MANUAL`, `decisionRule=MANUAL` 조합의 무채점 수동
  판정과 관리자 지정 1..N 순위
- 수상 근거 FK를 `REVIEW_ROUND_ENTRY`로 전환
- 가장 높은 `round_no`의 확정 라운드만 수상 후보 산출에 사용하며,
  `awardCount` 경계의 공동 순위는 계획 인원을 넘어도 전원 포함
- 확정 전 후보 상격(대상·특별상·총장상·사용자 정의 포함)과
  `CANDIDATE/HELD` 상태 편집, `HELD` 존재 시 전체 확정 차단
- 기존 수상 데이터의 `award_type` backfill과 상장번호 컬럼 확장은
  `docs/migrations/2026-08-09-award-type-and-certificate.sql`로 적용
- 후보 산출 뒤 `awardCount` 또는 최종 선정 결과가 달라지면 확정을
  거부하고 후보 재산출을 요구
- `AWARDED`는 일반 대회 수정 API로 진입하거나 이탈할 수 없고,
  최신 후보 검증을 통과한 수상 확정 트랜잭션만 상태를 전환

일반 라운드 확정 시 취소되지 않은 모든 배정은 `COMPLETED` 상태이고
`REVIEW` 한 건을 가져야 한다. 무채점 수동 라운드는 평가 기준과
배정을 만들지 않고 모든 ENTRY의 판정 사유와 순위를 요청에서
확정한다. 일반 entry의 `final_score`는 해당 entry에
대한 완료 심사 점수의 산술 평균이며 소수점 둘째 자리에서
`HALF_UP`으로 반올림한다. 같은 최종 점수는 같은 순위를 받고 다음
순위는 동점자 수만큼 건너뛴다. 예를 들어 `95, 90, 90, 80`은
`1, 2, 2, 4`다. `TOP_N` 경계가 공동 순위에 걸리면 해당 순위자는
전원 선정한다. entry ID는 동점자의 순위를 나누지 않고 조회와 처리
순서를 결정적으로 유지하는 데만 사용한다.

실제 MySQL에서의 잠금과 동시성 검증은
`RUN_MYSQL_INTEGRATION_TESTS=true`와 `MYSQL_TEST_URL`을 제공했을 때
실행되는 통합 테스트로 분리되어 있다. 여기에는 대상 준비부터 배정,
채점, 라운드 확정, 수상 후보 산출까지의 전체 흐름과 링크 인증 파일
다운로드 회귀 테스트가 포함된다. GitHub Actions의
`mysql-integration` 작업은 MySQL 8.4 서비스와 이 환경 변수를
준비해 해당 테스트를 별도로 실행한다. 이는 위 순방향 마이그레이션
리허설과는 별개의 검증이다.
