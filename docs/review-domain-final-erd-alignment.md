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

1. `review_round` 테이블을 생성한다.
2. 기존 `contest_stage` 중 심사 용도의 행을 `review_round`로 옮긴다.
3. 기존 값은 다음 기준으로 변환한다.

   | 기존 값 | 최종 ERD 값 |
   | --- | --- |
   | `sequence_no` | 대회 내 심사 순서인 `round_no` |
   | `PREPARING` | `PREPARING` |
   | `OPEN` | `OPEN` |
   | `COMPLETED` | `FINALIZED` |
   | `ALL_SUBMISSIONS` | `ALL_SUBMISSIONS` |
   | `PREVIOUS_PASSED` | `PREVIOUS_SELECTED` |
   | `pass_count` | `select_count` |

4. `review_criterion.review_round_id`를 nullable로 먼저 추가하고,
   기존 `contest_stage_id`를 이용해 값을 채운다.
5. `review_round_entry.review_round_id`도 같은 방식으로 추가하고,
   기존 `review_stage_id`를 이용해 값을 채운다.
6. 누락 값과 중복 값을 검사한 뒤 새 FK를 `NOT NULL`로 변경하고
   최종 유니크 제약을 생성한다.
7. 애플리케이션 전환과 데이터 검증이 끝난 후에만 예전 리뷰 FK 컬럼과
   제약을 제거한다.

다음 데이터는 자동 변환하지 말고 배포 전에 정책을 정해야 한다.

- 시작·종료 시각이 비어 있는 기존 심사 단계:
  `REVIEW_ROUND`는 두 값이 모두 필요하다.
- 기존 `FINAL` 판정 규칙:
  최종 ERD에는 같은 값이 없으므로 `TOP_N`, `MIN_SCORE`, `MANUAL` 중
  실제 운영 의미에 맞는 값으로 결정해야 한다.
- `COMPLETED` 행의 `finalized_at`:
  실제 확정 시각을 알 수 있으면 그 값을 사용하고, 알 수 없다면
  임의 시각을 넣기 전에 운영 정책을 확인한다.

개발용 DB처럼 기존 데이터를 보존할 필요가 없다면 백업 여부를 확인한
뒤 DB를 새로 만들어 최종 스키마를 생성할 수 있다. 운영 또는 공유
DB에서는 재생성하지 말고 위 순서의 명시적 마이그레이션을 사용한다.

## 현재 구현 범위

- 라운드 및 평가 기준 생성·조회·수정
- 전체 제출물을 대상으로 한 심사 대상 준비
- 심사위원 배정과 링크 기반 평가표 조회
- 항목별 점수 검증, 총점 계산, 중복 제출 방지
- 라운드 시작

`PREVIOUS_SELECTED`, `MANUAL` 대상 구성과 라운드 최종 점수·순위·판정
확정은 다음 구현 범위다. enum과 DB 구조는 최종 ERD 값으로 먼저
맞춰 두었지만, 현재 심사 대상 자동 준비는 `ALL_SUBMISSIONS`만 지원한다.
