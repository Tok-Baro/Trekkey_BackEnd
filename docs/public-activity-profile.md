# 외부 온체인 활동 프로필

학생이 직접 공개를 켠 경우에만 외부 검증자가 한 사람의 Kaia 앵커링 활동을 모아 볼 수 있다.

## 공개 범위

- 공개: 이름, 학교, 전공, `PUBLIC` subject로 발급된 온체인 Credential 요약
- 비공개: 이메일, 학번, 내부 사용자 ID, 제출 파일, 비공개 subject
- 대상 상태: `ANCHORED`, `REVOKED`, `SUPERSEDED`
- 개별 Credential은 기존 `/verify/{credentialPublicId}`에서 Merkle proof와 체인 상태를 다시 검증한다.

공개 프로필 ID는 UUIDv4이며 이름이나 학번으로 조회할 수 없다. 공유를 끄면 같은 URL이 즉시 404가 되고,
링크를 교체하면 기존 URL은 다시 사용할 수 없다.

## API

| Method | Path | 인증 | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/me/public-activity-profile` | 참가자 | 내 공개 설정과 공개 가능한 건수 |
| `PUT` | `/api/me/public-activity-profile` | 참가자 | `{ "enabled": true/false }`로 공개 전환 |
| `POST` | `/api/me/public-activity-profile/rotate` | 참가자 | 기존 URL을 폐기하고 새 URL 발급 |
| `GET` | `/api/public/activity-profiles/{publicId}` | 없음 | 공개 프로필과 온체인 활동 요약 |

운영 DB에는 `docs/migrations/2026-08-10-public-activity-profile.sql`을 먼저 적용한다.
