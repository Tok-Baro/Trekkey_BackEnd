# 최신 TREKKEY + Sui 전체 통합 검증·운영 인계

기록일: 2026-09-08. **격리 통합 검증 완료·운영 교체 보류** 기록이다. 최신 업무 코드와 Sui 이식을 통합한 뒤,
별도 영속 MySQL·실제 테스트넷·상시 HTTP 서버의 지정 업무 흐름과 재시작 후 데이터 보존을 확인했다.
로컬 테스트 통과를 원격 실행 성공, 실제 대학의 공식 승인, 운영/mainnet 전환으로 해석하지 않는다.

## 1. 기준 코드와 보존 경계

- 백엔드 Git HEAD: `0615984797b7c1c6b51f2f9e7ae7ca41332ae15c` (`0615984`).
  기존 운영 이미지의 기준과 일치하는 최신 업무 코드다. **이번 Sui 변경을 포함한 운영 배포 완료라는 뜻은 아니다.**
- 기존 checkout `e113959`에서 16개 commit을 fast-forward했고, autostash `822b911`로 기존 Sui/사용자 변경을 보존했다.
  README 충돌은 최신 업무 내용과 기존 변경을 함께 보존하도록 해결했다. 현재 산출물은 HEAD에 미커밋 변경을 더한 snapshot이다.
- 이번 추가 프론트 수정은 별도로 승인된 두 오류에 한정한다. 프론트 전면 Sui UX 개편 또는 전체 개인정보 정책 변경은 아니다.
- 기존 운영 컨테이너·운영 DB·운영 JAR, 앞선 격리 테스트 DB/JAR는 교체하지 않는다.
  최신 테스트 JAR는 버전이 구분되는 새 artifact directory에 두고 full override로만 선택한다.
- 서버 쓰기/기동/체인 실행은 승인된 배포 담당자만 수행한다. 이 문서 작성과 정적 검토는 원격 실행이 아니다.

### 운영 교체 조건 확인 — 현재 교체하지 않음

사용자는 조건부 운영 대체를 승인했지만, 실제 운영 DB의 읽기 전용 집계에서 기존 체인 발급 이력이 확인되어
**현재 단일 체인 설정으로 대체할 조건을 충족하지 않았다**. 운영 변경을 진행하지 않는다.

| 기존 운영 데이터 | 확인된 집계 |
| --- | --- |
| AWARD credential | ANCHORED 6개 |
| batch | ANCHORED 1개 |
| chain transaction | Kairos `chainId=1001`, CONFIRMED 1개 |
| outbox | PROCESSED 1개 |
| issuer key | 1개, version 1 |

현재 이식은 과거 Kaia/Kairos 증명과 새 Sui 증명을 한 설정에서 자동 라우팅하지 않는다.
Sui로 단순 교체하면 기존 6개 공개 검증을 유지할 수 없으므로 기존 운영 서비스/DB/설정을 보존한다.
legacy 공개 검증의 별도 READ_ONLY 제공 또는 다중 체인 routing·이관 방안을 승인·구현·검증하기 전 운영 전환 완료로 보고하지 않는다.

### 최신 통합 직전 소스 백업

저장소 상위 `backups/full-sui-integration-20260908.ZsF6YP/backend-before-latest.tar.gz`.

```text
SHA-256 c0f23936307c109339a43b263734a4ed4b9ab9eb270077a69d811e1e98d1fd3a
```

Git 이력·미커밋·미추적·ignored 파일을 포함할 수 있는 **비공개 소스 백업**이며 외부 업로드하지 않는다.
DB dump, uploads, 키 보관소, journal, 이미 기록된 체인 상태의 백업은 아니다. 복구 시 새 빈 디렉터리에 먼저 풀어
원본과 비교한다. 현재 checkout에 덮어쓰거나 `git reset --hard`로 사용자 변경을 지우지 않는다.

### 별도 EC2 온라인 운영 snapshot

조건 확인 과정에서 서버 내부 `/srv/trekkey-backups/pre-replacement-20260908-online`에 별도 운영 snapshot을 보존했다.
디렉터리는 root 소유 0700, artifact는 0600이다. 소스 tar와 다른 백업이며 외부로 전송하지 않는다.

| artifact 종류 | 크기 |
| --- | --- |
| `database.sql.gz` | 39,286 bytes |
| configuration archive | 1,896 bytes |
| uploads archive | 107 bytes |
| production image archive | 193,050,844 bytes |

gzip 검사 PASS. `SHA256SUMS` **파일 자체의 SHA-256**은
`095b8191d8084e6c822fba492c716230717451defd2cea7092eb54383ce142b2`다.
이는 서비스가 켜진 상태의 **온라인 snapshot**이다. 이후 `network=none`의 별도 MySQL에서 실제 복원하여
48 tables와 ANCHORED AWARD 6개/batch 1개/CONFIRMED transaction 1개/issuer key 1개 보존을 확인했다.
복원 감사 컨테이너는 정지했고 최종 `running=false`도 재확인했으며 volume은 보존했다. 이 DB restore 성공은 최종 write-paused 일관성 백업이나
파일·설정·앱을 포함한 전체 운영 복구 검증과 다르다. 운영 교체는 실행하지 않았다.

## 2. 최종 검증 현황 — 완료 범위와 한계

다음은 배포 담당자가 실제 실행·관찰해 전달한 결과를 기준으로 한다. 지정 검증의 완료와 제품 전체 검증·운영 전환은 구분한다.

| 검증 | 현재 결과 | 의미 / 남은 범위 |
| --- | --- | --- |
| 최신 Java 로컬 전체 + build | **760개: 733 PASS / 27 SKIP / 실패 0, 117 suites, build PASS(22초)** | 과목 HTTP transaction·UTC precision·Sui metadata 수정 포함. 25개 전용 MySQL + Sui opt-in 2개는 로컬에서 skip |
| 승인 범위 프론트 회귀·build | **29/29 PASS, production build PASS** | 실제 브라우저-서버 전체 E2E 완료와 다름 |
| 격리 인프라/HTTP 도구 | **19/19 PASS** | 기존 준비 도구 9개 + HTTP 검증 도구 5개 + 재시작 readback 도구 5개. 합성 입력/오프라인 계약 검사 |
| 백업 SQL 안전 guard | **11/11 PASS** | SQL 대상/허용 범위 회귀. 실제 DB restore 결과는 별도 기록 |
| 새 DB/user 두 쌍 생성 | **완료** | `trekkey_test` / `trekkey_it_suite`, `trekkey_sui_full` / `trekkey_sui_full`; 각각 정확한 전용 schema 권한만 부여 |
| 최신 full runtime schema/기동 | **48 tables bootstrap + 일반 앱 validate + v6 18080 적용 완료** | 기존 30-table DB와 별개. v6 JAR/bundle의 원격 SHA-256 일치 확인 |
| 전용 MySQL 9개 class / 25개 test | **25/25 PASS** | clean v3로 실제 전용 MySQL 실행 완료. 첫 포장 오류 시도는 검사 0건으로 별도 보존 |
| 과목 HTTP transaction 회귀 | **4/4 PASS** | 수정 전 4개 중 3개 실패를 재현한 뒤 controller transaction 경계 2곳 수정. 745개 전체/JAR v4 및 실제 HTTP 검사에 반영 |
| 영속 MySQL → 실제 Sui 3종 발급/취소 | **실제 resume 1/1 PASS, 총 4 CONFIRMED** | 3종 VALID 확인 후 PARTICIPATION REVOKED, WORK/AWARD VALID. 기존 2거래·서명 bytes와 원문/hash 불변 확인 |
| UTC precision + verification + issuance 좁은 회귀 | **26/26 PASS** | 수정 전 24개 중 5개 실패 보존. 정확한 DB floor/half-up 표현만 허용, 기타 미세시간 변이 거부 |
| 상시 서버 로그인·졸업·외부 증빙 HTTP | **27 checks PASS** | 실제 18080 로그인·역할 거부·2인 승인·과목 import/mapping·평가 경로. 모든 졸업 규칙의 정답 검증은 아님 |
| 기존 프론트 Chrome 화면 | **지정 공개 흐름 확인** | 재시작 후 PARTICIPATION 폐기 안내·lookup으로 AWARD 유효/대상 화면 확인. 전체 33-route/인증 브라우저 E2E는 아님; 네트워크 '-' 표시 cosmetic 한계 |
| 재시작 후 영속 공개 검증 | **GET 3개 + PDF/ZIP 6개 PASS** | 별도 프로세스/SSH 터널에서 PART REVOKED·WORK/AWARD VALID, claims 모두 true, 바이너리 HTTP 200/magic 확인 |
| 재시작 후 업무 데이터 보존 | **8 checks PASS** | 학생 sign-in 1회 + GET 7개. profile/증빙/과목·기관 mapping/증명서와 기존 2 digest 재확인. mapping의 과거 unit ID 동일성까지 검증한 것은 아님 |
| 기존 운영·DB 분리 보존 | **최종 읽기 대조 PASS, 운영 교체 없음** | 기존 운영 컨테이너 2개·HTTP 200·Kairos 발급 집계 유지, old test 30 tables/full 48 tables |

이번 실행의 공개 요약은 [최신 영속 통합 manifest](deployments/sui-full-integration-2026-09-08.json)를 참조한다.

앞선 공개 테스트넷 배포와 Java·H2 발급/취소 성공은 [이전 실행 기록](deployments/sui-testnet-2026-09-08.json)에 있다.
그 기록의 JAR SHA와 임시 H2 증명서는 **이번 최신 영속 MySQL 산출물/증명서가 아니다**.
Move 42개·gateway 53개 등 앞선 검사도 이번 최신 Java 전체 실행과 구분하여 이전 [이식 기록](sui-migration-2026-09-08.md)을 참조한다.

승인된 프론트 두 수정은 다음과 같다. JSX handler/hook harness 회귀 10개를 포함한 **29/29, skip 0**과
production build 성공(2.78초)을 확인했으며 실제 React DOM/브라우저 QA는 이 수치에 포함하지 않는다.

- `Trekkey/src/pages/participant/ExternalEvidencePanel.jsx`: `event.currentTarget`을 await 전에 저장하여 비동기 이후 form reset 실패 방지.
- `Trekkey/src/components/forms/ContestApplicationForm.jsx`: `contest.maxTeamMembers`에서 총정원/대표자 제외 정원·안내·검색/추가 제한·제출 검증을 함께 계산.
  개인전 1명, 미설정/무효 값은 기존 호환값 5명. 기존 dirty README/CSS와 다른 앱 파일은 이 수정에서 건드리지 않았다.

후속 [GraduationCourseController](../src/main/java/com/api/trekkey/domain/graduation/web/controller/GraduationCourseController.java) 수정은
목록에 read-only transaction, PATCH에 쓰기 transaction을 두어 OSIV가 꺼진 실제 HTTP 경계에서도 profile/학사 단위 접근과
DTO 변환·mapping 저장을 마치도록 한다. [전용 회귀](../src/test/java/com/api/trekkey/domain/graduation/integration/GraduationCourseHttpTransactionIntegrationTest.java)는
test 전체 transaction 없이, OSIV=false·실제 보안/MVC/repository를 사용한다. 수정 전 3개 실패 → 수정 후 4/4 통과했으며
다른 학생/타 기관 단위 접근 거부도 검사한다. 이 좁은 회귀는 H2 기반이다. 이후 실제 상시 서버의 27 HTTP checks도 별도로 통과했다.

## 3. 전용 환경 계약

기준 서버 경로는 `/srv/trekkey-sui-testnet`. 기존 [격리 실행 runbook](sui-testnet-server-runbook.md)의
loopback·비밀 파일·자원 제한·중단 기준은 계속 적용한다. host network는 네트워크 샌드박스가 아니다.

| 대상 | 고정 계약 |
| --- | --- |
| MySQL | `127.0.0.1:13306`, 기존 테스트 MySQL 내 **서로 다른 새 schema/user** |
| disposable MySQL 회귀 | schema `trekkey_test`, user `trekkey_it_suite`; 각 class가 `create-drop`, 다른 DB 대상 금지 |
| 영속 full 앱/E2E | schema/user `trekkey_sui_full`; 앱과 runner 모두 `ddl-auto=validate`, 기존 데이터 삭제 없음 |
| 상시 full Java | `127.0.0.1:18080`; 초기 빈 schema bootstrap만 `18081`, 확인 후 종료 |
| Sui gateway | `127.0.0.1:9187`; 기존 검증된 package/Registry/기관 키/journal 재사용, 추가 배포 없음 |
| full JAR | `TESTNET_FULL_ARTIFACT_DIR/app.jar`, 검증된 버전별 절대경로를 명시; 기존 `artifacts/app.jar` 덮어쓰기 금지 |
| full 파일 | `uploads-full` → 앱/runner의 `/uploads`; UID10001, 0700 |
| full 실행 증거 | `full-e2e` → runner의 `/run-e2e`; UID10001, 0700 |
| full DB/login 비밀 | `secrets/full/DATASOURCE_PASSWORD`, `E2E_LOGIN_PASSWORD`; UID10001, 0600 |
| MySQL 회귀 비밀 | `secrets/mysql-suite/MYSQL_TEST_PASSWORD`; UID10001, 0600 |

full override: [`infra/testnet/full-integration.override.yml`](../infra/testnet/full-integration.override.yml).
일반 앱은 **READ_ONLY / worker=false**다. READ_ONLY는 체인 쓰기만 제한하며 일반 업무 DB 쓰기를 막지 않는다.
영속 E2E만 명시적으로 LOCAL_RELAYER와 수동 worker를 사용한다. 자동 worker나 mainnet 쓰기를 켜지 않는다.

신규 DB 준비 도구는 SQL 파일을 만들 뿐 SQL을 실행하지 않는다. 별도 확인 후 정확한 격리 MySQL에서만 실행한다.
DB/user가 없다는 사전 확인과 EXCL claim이 필요하며, 기존 schema에 `IF NOT EXISTS`로 조용히 합류하지 않는다.
MySQL schema grant의 `_`는 escape하여 비슷한 이름의 schema까지 권한이 넓어지지 않게 했다.

- [full DB 준비](../infra/testnet/prepare-full-database.mjs)
- [disposable MySQL 준비](../infra/testnet/prepare-mysql-suite.mjs)
- [회귀 실행 wrapper](../infra/testnet/run-mysql-suite.sh)

`setup.sql`에는 새 비밀번호가 포함되므로 출력·Git 추가·일반 artifact 업로드를 금지한다.
이미 DB/user 생성이 완료된 현재 환경에서 준비 도구나 setup.sql을 다시 실행하지 않는다.

## 4. 영속 업무 → 증명 lifecycle의 수락 기준

구현: [SuiPersistentWorkflowIntegrationTest](../src/test/java/com/api/trekkey/domain/credential/integration/SuiPersistentWorkflowIntegrationTest.java),
[standalone runner](../src/test/java/com/api/trekkey/domain/credential/integration/SuiPersistentWorkflowE2ERunner.java).

합성 학교 UUID는 기존 testnet manifest와 같고 기관 ID는 `Hashing.issuerId(publicId)`로 대조한다.
학교 코드 `HANSUNG_UNIVERSITY`는 후속 졸업 규칙 조회를 위한 테스트용이며 실제 학교의 승인을 뜻하지 않는다.
합성 관리자 2명·학생 1명, 대회·팀·LEADER 관계만 bootstrap하고, 아래 발급 전이는 실제 인증된 MVC/controller/service로 수행한다.

| 업무 동작 | 검증되는 산출물 |
| --- | --- |
| 학생 multipart 제출 | 실제 파일 저장·SHA-256·submission 행 |
| 관리자 팀 승인 → 참가 확정 | READY `PARTICIPATION` 1개 |
| 제출 마감 후 수동 심사 라운드 open | 제출 확정 + READY `WORK` 1개 |
| MANUAL/MANUAL 심사 `SELECTED` 확정 → 수상 후보 산출/확정 | 대회 AWARDED + READY `AWARD` 1개 |
| 각 schema별 seal → 기관 승인 → outbox/수동 worker | 3개 Sui anchor, DB CONFIRMED, 실제 checkpoint/Registry readback |
| 공개 JSON + ZIP/PDF | 3개 VALID, 내용/hash/Merkle/기관/체인 증거 일치와 바이너리 형식 |
| PARTICIPATION 폐기 승인 → 동일 처리 경로 | REVOKED + 공개 재조회/ZIP/PDF, 4번째 CONFIRMED 거래 |

심사위원 채점·이메일 심사 링크 전체 경로는 이 수동 심사 시나리오의 검증 대상이 아니다.
SUPERSEDED는 이번 실제 테스트넷 4거래 시나리오에 포함하지 않는다.

- 초기 credential/outbox/chain transaction은 모두 0개여야 한다. 앱 설정·네트워크·기관 키를 확인한 다음 `run.claim`을
  CREATE_NEW/0600으로 fsync하고 첫 DB 쓰기를 시작한다. 기존 claim은 성공/실패와 무관하게 재실행을 거부한다.
- 기관 승인 최대 4개, sidecar gas budget 50,000,000 MIST/거래, 최대 합계 200,000,000 MIST = **0.2 testSUI**.
  이 값은 예산 상한이며 실제 소비량을 뜻하지 않는다. 운영자는 `SUI_FULL_GAS_BUDGET_MIST=50000000` 전에 실제 gateway 설정을 확인한다.
- Java DB에 exact signed envelope를 저장/commit한 뒤 전송한다. gateway journal도 동일 승인/가스 예약을 유지한다.
  응답 불명확 시 새 서명/새 nonce로 대체하지 않고 저장된 같은 bytes/signature/digest만 조정한다.
- 01-fixture/result에는 합성 이메일과 DB/public IDs만 기록한다. 로그인 암호는 보호 파일에서 읽어 BCrypt로 저장하고,
  기관 키·JWT·gateway token·signed envelope는 stdout/공개 문서에 넣지 않는다.
- `result.json` PASS, 4개 CONFIRMED, outbox PROCESSED, 실제 public 상태를 모두 확인해야 완료다.
  개수만 맞거나 skip/0건인 결과는 통과가 아니다. 영속성은 별도 프로세스/상시 서버 재조회로 확인해야 한다.

## 5. 재현·실행 순서 요약

아래는 승인된 담당자가 확인한 산출물과 보호 mount를 준비한 뒤 실행하는 절차다. 자동 배포 명령이나 무조건 재실행 안내가 아니다.

1. 최신 Java JAR/testRuntimeClasspath bundle의 SHA-256을 원격 복사 전후 대조한다. 테스트 묶음에는 compiled main/test와
   JUnit launcher를 포함한 runtime library가 필요하다. 키·`.env`·소스 전체 백업은 묶지 않는다.
   macOS tar 포장은 `COPYFILE_DISABLE=1`을 적용하고 압축 목록에 `._*.class`/`__MACOSX`가 없는지 검사한다.
2. Compose base와 full override를 함께 검사한다. 기존 운영 포트/컨테이너와 자원 여유를 기록한다.
3. **새 빈 `trekkey_sui_full`의 0 tables를 확인한 경우에만** 명시 bootstrap으로 최신 schema를 만들고 정상 종료한다.
   일반 앱을 validate로 기동한다. 실패를 숨기려고 일반 앱을 update로 바꾸지 않는다.
4. disposable MySQL 25개를 별도 JVM/전용 user로 실행한다. 그 뒤 영속 Sui runner를 정확히 한 번 실행한다.
5. 성공/실패 모두 claim, DB, journal, uploads, artifact를 보존한다. 상시 HTTP 검증은 별도 one-shot claim을 사용한다.

검증된 full artifact directory를 `TESTNET_FULL_ARTIFACT_DIR`로 지정한 뒤, 백엔드 저장소 경로에서:

```sh
docker compose --env-file /srv/trekkey-sui-testnet/deploy.env \
  -f infra/testnet/docker-compose.yml -f infra/testnet/full-integration.override.yml config --quiet
```

전용 Java 21 tool container의 MySQL 회귀 entrypoint는 `/bin/sh /app/infra/run-mysql-suite.sh`다.
wrapper는 상속 환경을 비우고 보호 파일을 읽은 뒤 허용된 9개 class만 순차 실행한다.
`SUI_*`, 별도 Spring profile/config import, Java option 주입이 있으면 runner가 거부한다.
모든 class 성공 시 `MYSQL_SUITE_RESULT classes=9 expected=25 succeeded=25 status=PASS`가 출력된다.

영속 runner는 아래 **비밀값 없는 환경 계약**을 사용한다. 실제 container mount 경로는 실행 담당자가 대조한다.

```text
SUI_FULL_E2E=true
SUI_FULL_DB_PASSWORD_FILE=<0600 mounted dedicated DB password file>
SUI_FULL_LOGIN_PASSWORD_FILE=<0600 mounted synthetic 64hex login password file>
SUI_FULL_RUN_DIR=/run-e2e
SUI_FULL_UPLOAD_DIR=/uploads
SUI_FULL_GAS_BUDGET_MIST=50000000
SUI_E2E_STATE_DIR=<0700 mounted synthetic deployment state directory>
SUI_GATEWAY_TOKEN_FILE=<0600 mounted gateway token file>
SUI_E2E_GATEWAY_URL=http://127.0.0.1:9187
```

```sh
java -Djava.awt.headless=true -cp '/app/e2e/main:/app/e2e/test:/app/e2e/lib/*' \
  com.api.trekkey.domain.credential.integration.SuiPersistentWorkflowE2ERunner
```

이 테스트용 tool container만 합성 issuer key를 읽는다. 일반 앱/gateway에는 deployment state directory 전체를 mount하지 않는다.
runner는 framework의 임의 로그/예외 메시지를 숨기고 공개 결과·거래 digest·실패 type/source location만 출력한다.

## 6. 상시 HTTP 검증과 기록

[verify-full-http.mjs](../infra/testnet/verify-full-http.mjs)는 고정 origin `http://127.0.0.1:18080`과 허용 API만 호출한다.
영속 fixture/claim과 보호 로그인 파일을 대조한 뒤 `http-run.claim`을 fsync한다. 로그인도 refresh session을 저장하므로 쓰기다.

```sh
node infra/testnet/verify-full-http.mjs --run --confirmed-isolated-full-database
```

전용 서버 root/mount 검증을 거친 담당자만 실행한다. 토큰·비밀번호·HTTP 본문을 로그에 출력하지 않는다.
목표는 3계정 실제 로그인, 역할별 접근 거부, 졸업 profile/평가, 합성 PDF 업로드, 관리자 1차 승인,
동일 관리자 중복 승인 거부, 다른 관리자 2차 승인/VERIFIED, CSV 과목 import·학사 단위 mapping·재평가다.
성공해도 공식 졸업 자격 판정의 정확성이나 native credential import 행 개수까지 입증하는 것은 아니다.
정책 sync/publish를 이 도구가 실행하지 않으며 관측된 평가 상태를 임의로 ELIGIBLE로 바꾸지 않는다.

첫 영속 one-shot은 2개 anchor 후 실패했고, 기존 claim/fixture/거래를 보존했다. 이후 v5 UTC 수정 적용으로
기존 WORK의 실제 HTTP 200/VALID·claims=true·동일 digest를 확인했다. 새 발급이나 원문 수정으로 우회하지 않았다.
상시 서버의 27 HTTP checks와 일반 앱 schema validate/18080 HTTP 확인도 PASS다.
이후 v6 원격 SHA-256 대조·18080 적용과 동일 상태 resume가 완료되어 `/srv/trekkey-sui-testnet/full-e2e/result.json`을 저장했다.
실제 runner **1/1 PASS**, 총 4 CONFIRMED이며 최종 PARTICIPATION REVOKED, WORK/AWARD VALID를 확인했다.
이후 v6 격리 백엔드를 재시작하고 별도 HTTP로 공개 JSON 3개와 PDF/ZIP 6개를 재확인했다.
Chrome reload에서는 PARTICIPATION 폐기 안내를, lookup으로 이동한 AWARD 화면에서는 유효 상태와 '대상'을 확인했다.
네트워크 '-' 표시는 cosmetic 한계이며 이 관찰을 전체 33-route/인증 브라우저 E2E나 모든 다운로드의 시각 QA로 계산하지 않는다.

재시작 후 업무 보존 검사는 [verify-full-persistence.mjs](../infra/testnet/verify-full-persistence.mjs)와
[전용 container wrapper](../infra/testnet/run-persistence-container.sh)가 수행한다. 고정 격리 origin, 기존 fixture·HTTP artifact·
최종 chain result·보호 로그인 파일을 대조하고 별도 `restart-readback.claim`을 EXCL/fsync한다.
실제 **8 checks PASS**: 학생 로그인 1회와 GET 7개이며, 로그인 외 업무 수정·체인 쓰기는 하지 않는다.
로그인 자체는 refresh session을 저장하므로 순수 읽기 전용 실행으로 부르지 않는다.

```sh
/bin/sh infra/testnet/run-persistence-container.sh
```

위 wrapper는 승인된 서버 root와 고정 mount를 확인한 담당자만 실행하며, 이미 claim이 있는 현재 환경에서는 재실행하지 않는다.
`restart-readback.json` 결과와 모든 원래/HTTP/재개 claim을 보존한다. 비밀값·원문·전체 응답은 이 문서에 복사하지 않는다.

## 7. 중단과 복구

- claim 생성 후 실패하면 자동 재시작/삭제/처음부터 재실행하지 않는다. `01-fixture.json`, 배치/status IDs,
  DB outbox/chain transaction, gateway journal의 digest를 먼저 읽기 전용으로 대조한다.
- chain receipt가 이미 성공했으면 새 발급을 만들지 않는다. 기존 처리의 동일 signed bytes와 readback으로 reconcile한다.
  성공 여부가 불명확한 transaction을 TTL 만료라는 이유로 journal에서 지우지 않는다.
- bootstrap이 끝나지 않았으면 해당 테스트 bootstrap만 정지한다. 일반 앱과 운영 앱에 ddl update를 켜지 않는다.
- full 앱 문제는 이 Compose 프로젝트의 `backend`만 정지하고 버전이 확인된 이전 테스트 JAR/기본 설정으로 복귀할지 결정한다.
  full DB·uploads·journal을 유지한다. 기존 test DB/JAR가 남아 있다고 full DB의 snapshot이 복원되는 것은 아니다.
- `docker compose down -v`, Docker prune, schema DROP, 주소 컬럼 축소, 운영 컨테이너 재시작은 복구 절차가 아니다.
- 실제 DB rollback에는 별도의 일관된 DB/파일/journal 백업이 필요하다. 이번 소스 tar 백업으로 DB 복원 완료를 주장하지 않는다.
- 체인에 기록한 testnet 거래는 소스/JAR rollback으로 삭제되지 않는다. mainnet·실제 대학 키·실제 개인정보로 재현하지 않는다.

## 8. 제품·보안 한계

이번 Move/승인 ABI는 기관 ID·증명/배치 hash·Merkle root·version·nonce·시간 등 고정 필드만 기록하며 원문 파일/이름/이메일을 전송하지 않는다.
새 runner는 승인 전 hash/numeric field whitelist와 이후 실제 BCS transaction 내 합성 원문 미포함을 검사한다.
이는 **이번 합성 경로의 원문 비포함 검사**이며 전체 개인정보 보호 감사나 익명성 증명은 아니다.
오프체인 공개 credential/ZIP/PDF에는 기존 공개 정책에 따른 subject snapshot 등이 남을 수 있다.

- Sui 서명·Merkle 증명은 등록 기관 키의 승인과 저장 내용의 무결성을 확인한다. 입력 사실의 진실성이나 공식 학사 자격을 보증하지 않는다.
- 이번 합성 institution/관리자 승인은 실제 대학과 연계된 검증이 아니다. MANUAL 심사도 실제 지원되는 업무 흐름이지 외부 사실 검증이 아니다.
- Walrus·Seal 저장/암호화/학생별 키 배포·복호화 권한·MemWal/AI는 **미구현**이다. Sui 이식만으로 파일 회수·삭제권·공유 취소가 생기지 않는다.
- KMS/HSM, 실제 대학 온보딩·키 소유권, 운영 키 회전/침해 대응, 오프사이트 복구, gas/RPC SLA, 부하·장애·독립 계약 보안감사는 별도 gate다.
- 과거 Kaia 증명을 새 Sui 설정으로 자동 라우팅/재발급하지 않는다. legacy 검증과 migration/rollback 계획이 필요하다.
- 기존 chainId=0 호환 sentinel, 프론트 Kaia/EIP-712 문구와 Sui 탐색기/지갑 UX 제한은 별도 범위다.
- 실제 Chrome에서 유효 AWARD의 'Proof 직접 재계산'으로 연 Tamper Lab은 서버 원문 claim·6×32-byte word→leaf·0단계 proof→root가 일치했지만, 기존 Kaia 기록 검사 때문에 'Kaia 공개 기록 없음'·'증거 불일치 확인 필요' 경고와 Chain 0을 표시했다. 일반 공개 검증은 정상이나 **Tamper Lab의 Sui 전체 호환은 미완료**이며 단순 cosmetic 문제로 한정하지 않는다.
- Tamper Lab 카드의 Java 629개/프론트 19개 고정 문구도 과거 값이다. 승인된 두 UI 오류 외 코드 수정은 하지 않았으며, 프론트 전체 호환 완료로 주장하지 않는다. 이는 legacy 증명 6개 보존을 기본 사유로 둔 **운영 완전 교체 보류의 추가 근거**다.
- 졸업 규칙과 외부 증빙의 HTTP 검증은 해당 코드 경로가 실행됨을 뜻한다. 모든 입학 연도/학과/예외/공식 문서의 정확성을 전수 검증한 것은 아니다.

## 9. 실제 실행 기록 — 완료와 실패 이력

- 초기 full bootstrap JAR: `/srv/trekkey-sui-testnet/artifacts/full-integration-0615984/app.jar`,
  SHA-256 `1ce0842a3840dd131a19d0244b2eea2e437c7eb83d939286af490d4055dd3824`.
  기존 운영/이전 테스트 JAR를 덮어쓰지 않았다.
- 과목 HTTP transaction 수정 포함 v4 JAR SHA-256:
  `e13a5477dd1b79f94272bafaba570920ef7909918bf62e99642019dfe59edb11`.
  UTC precision 수정은 이 v4 JAR에 포함되지 않는다.
- UTC 수정 포함 v5 JAR SHA-256: `2776c28f2a1ae9af1b78a1c7b21e1f11d10075acc3fc9aaa3505d188c3cabc65`.
  **실제 적용 완료**. 기존 WORK HTTP 200/VALID, `credentialClaimsMatch=true`, 기존 transaction digest 동일을 확인했다.
- 최신 UTC+metadata 수정 포함 v6 산출물: Java 전체 **760개/733 PASS/27 SKIP/실패 0, 117 suites**, build PASS(22초).
  JAR SHA-256 `a09848b668594a3986c9d90485dfa144c6e53542083bb7c3e62486d247316b58`,
  clean bundle SHA-256 `7f7489c1441406ba15b96a318071af7ee84754e0c3f35f740869c31ffdadb512`.
  **두 원격 SHA-256 일치, 18080 적용 완료, 동일 상태 resume 실제 PASS**를 확인했다.
- full schema bootstrap: **48 tables, HTTP 200 확인**. 이후 일반 full 앱의 `ddl-auto=validate` 기동과
  `127.0.0.1:18080` HTTP 200도 **완료**. 초기 bootstrap과 후속 v6 적용을 각각 확인했다.
- MySQL 25개 첫 시도: `ClassFormatException`으로 context 시작 실패. macOS tar의 AppleDouble `._*.class`가 원인으로 확인됐다.
  **실제 테스트 0건 실행, 해당 disposable DB schema의 테스트 테이블 생성도 없음**. 25개 통과/실패로 계산하지 않는다.
  v1/v2 묶음은 실행 불가 포장 artifact로 보존하며 덮어쓰지 않는다. `COPYFILE_DISABLE=1` clean v3로 재포장했다.
  v3 bundle SHA-256: `27e59175cfccf19719f3e3c71aa48ace0d068a82a0ea2b8020f680b954313ee2`.
  실제 MySQL 재실행에서 **9개 class / 25개 모두 PASS**를 확인했다.
  이 문제 해결을 위해 DB 생성/삭제, 키·package·Registry 재발급, journal 삭제를 하지 않았다. 운영 영향 없음.
- 영속 Sui **첫 실행 당시에는 FAIL**. 두 anchor는 DB CONFIRMED이나 WORK 공개 검증이 3분 동안 VALID가 되지 않아
  `SuiPersistentWorkflowIntegrationTest.awaitPublicStatus`에서 timeout했다. 거래 성공만으로 전체 E2E 성공 처리하지 않는다.
  - PARTICIPATION `c616a240-e5be-4021-b144-f78e879b9a2b`: **첫 실행 당시 VALID**,
    tx `391PcEPC2irKkmga4ybFUArZMYvkzByyDsELTuDRxk6C`, checkpoint `381273062`.
  - WORK `54a0c3ec-925c-4b41-bcb0-36cfbc487db3`: **첫 실행 당시 TAMPERED**, 6개 로컬 증거 중 `credentialClaimsMatch`만 false,
    tx `4pJJ6px463hFXxvEKUD5MQ16jrWZVoACjkUtppoANej6`, checkpoint `381273117`, DB 거래 CONFIRMED.
  - 첫 실행 당시 AWARD는 PENDING, 로컬 claims는 true였으며 AWARD anchor/폐기는 실행하지 않았다.
  - 원인 확정: WORK canonical `issuedAt=2026-09-08T12:32:29.868396862Z`, MySQL metadata
    `2026-09-08 12:32:29.868397`의 정당한 반올림을 기존 verifier가 TAMPERED로 오인했다. DB credential 상태는 ANCHORED다.
    참여 `.718983199→.718983`, 수상 `.492097432→.492097`은 기존 절삭 비교에도 일치했다.
    MySQL의 fractional precision 변환은 [공식 문서](https://dev.mysql.com/doc/refman/8.4/en/fractional-seconds.html)를 참조한다.
  - 수정: canonical 원문/JSON/content hash/서명/기존 행은 바꾸지 않고, duplicated metadata에 대해 exact equality 또는
    canonical에서 계산한 정확한 microsecond floor/half-up만 허용한다. 임의 ±1μs 허용은 아니다.
    새로운 issuance metadata는 명시적으로 μs 절삭하고 재시도 identity는 immutable canonical timestamp까지 정확히 대조한다.
  - 회귀: actual WORK 시간, 반올림 임계점, 초/날짜/연도 경계, 잘못된 다음 μs·나노정밀 metadata 변경 거부,
    원문/hash 불변, 같은 μs에 속해도 다른 1ns issuance 요청 거부를 포함한 **26/26 PASS**.
    real opt-in은 로컬에서 1개 skip. 이후 v5 실제 WORK **HTTP 200/VALID·claims=true·동일 digest 확인 완료**.
    나머지 2거래는 아래 v6 제한 재개에서 완료했다.
  - 기존 claim/batch/outbox/서명/nonce/journal을 보존했다. fresh duplicate run은 금지한다.
- v6 동일 상태 resume: **exit 0**, `SUI_FULL_RESULT found=1 succeeded=1 failed=0 skipped=0 aborted=0 failedContainers=0 status=PASS`.
  `/srv/trekkey-sui-testnet/full-e2e/result.json` 저장을 확인했다. 원래 두 anchor를 재발급하지 않고
  AWARD anchor와 PARTICIPATION 폐기만 추가했다. 최종 **3 batches / 1 status / 4 CONFIRMED / 4 PROCESSED outbox**다.
  - 3종 각각 VALID를 확인한 뒤 PARTICIPATION을 REVOKED로 전이했고, WORK/AWARD는 VALID를 유지했다.
  - 기존 두 transaction digest와 exact signed bytes, 세 credential의 canonical/content hash 불변 assertion이 통과했다.
  - 공개 PDF/ZIP 응답은 VALID 3종의 6개 + REVOKED의 2개, 총 **8개 PASS**다.
    이는 응답 상태·바이너리 형식 검사이며 PDF 전 페이지 시각적 품질 검사나 브라우저 다운로드 QA는 아니다.

| 실제 거래 | transaction digest | checkpoint |
| --- | --- | --- |
| PARTICIPATION anchor — 기존 거래 보존 | `391PcEPC2irKkmga4ybFUArZMYvkzByyDsELTuDRxk6C` | `381273062` |
| WORK anchor — 기존 거래 보존 | `4pJJ6px463hFXxvEKUD5MQ16jrWZVoACjkUtppoANej6` | `381273117` |
| AWARD anchor — 제한 재개 | `5KDLVPwr6W9upTBAWdBsLeoeLcDX2rWTGqYme53jJ3Qh` | `381280961` |
| PARTICIPATION revoke — 제한 재개 | `7aKBJK8hABvhVoLJFtb4AsP4bR9ByAu83qcQ8hgJUESP` | `381281042` |

AWARD checkpoint digest: `2LEgzebLdjGYhG4mgSLEbfFmL6pRwK4b7sLvkv3TusBp`.
[이번 영속 실행 manifest](deployments/sui-full-integration-2026-09-08.json)는 이전 H2 manifest와 구분한다.

- 상시 HTTP 실제 **27 checks PASS**: 3계정 로그인, 역할 거부, 졸업/외부 증빙·2인 승인·과목 import/mapping·재평가.
- v5 적용 후 같은 DB의 WORK 공개 검증과 digest 보존을 확인했고, v6 resume에서 최종 4거래와 공개 상태 검사를 통과했다.
- v6 격리 백엔드 container `028c3fc88161` 재시작 시작: `2026-09-08T13:02:08.761Z`.
  기동 완료 뒤 별도 프로세스/SSH 터널의 실제 GET 3개에서 PARTICIPATION REVOKED, WORK/AWARD VALID,
  `credentialClaimsMatch=true`를 모두 확인했다. PDF/ZIP 6개도 HTTP 200과 magic 검사 PASS다.
- Chrome 기존 UI에서 PARTICIPATION을 reload하여 '발급기관이 취소한 증명서입니다' 안내를 확인하고,
  lookup으로 AWARD에 이동하여 유효 수상 '대상' 화면을 screenshot으로 확인했다.
  폐기 전 PARTICIPATION 유효 표시·PDF/ZIP 버튼 관찰도 별도 이력으로 남긴다.
  이는 전체 33-route/인증 브라우저 E2E가 아니며 네트워크 '-' 표시가 남는다.
- 재시작 후 업무 readback: `2026-09-08T13:07:45Z`~`13:07:51Z`, **8 checks PASS**(학생 sign-in 1회 + GET 7개).
  - profile public ID와 CSV 총 3학점이 유지됐다.
  - 외부 증빙은 같은 public ID, VERIFIED, 2 reviews, assurance L2와 file hash를 유지했다.
  - 과목은 같은 public ID, MAJOR_REQUIRED와 기관 학사 단위 mapping 1개를 확인했다.
    최초 HTTP artifact에 원래 unit ID가 없어 **현재 기관 단위 목록에 mapping이 존재함만 검증**했다.
    과거 unit ID와 동일하다고 주장하지 않는다.
  - PARTICIPATION REVOKED, WORK/AWARD VALID 및 기존 PARTICIPATION/WORK 두 anchor digest 보존을 재확인했다.
  - 첫 도구 실행은 `FOWNER` 부재로 `writeExclusive`의 chown 이후 chmod에서 실패했다.
    **HTTP 호출 전, 정확히 0-byte claim·result 없음**을 확인한 뒤
    `restart-readback-empty-before-http.claim`으로 보존했다(삭제 없음).
    wrapper에 필요한 `FOWNER` capability만 추가하고 본 검증을 통과했다.
    원래 `run.claim`, `http-run.claim`, `resume-two-anchors.claim`과 최종 readback claim/result는 모두 보존했다.
  - 이 readback 도구의 오프라인 5개를 포함해 인프라 **19/19 PASS**, 별도 SQL restore guard **11/11 PASS**를 새로 확인했다.
- 최종 운영 대조: 기존 container IDs `62d804a4e4b2`/`d5dc8757e07b` 유지, production HTTP 200 및
  합성 확인 keyword `SYNTHETIC` 일치. 읽기 전용 DB 집계는 ANCHORED AWARD 6개/batch 1개/CONFIRMED transaction 1개/key 1개로 유지됐다.
  앞선 격리 DB는 30 tables, 새 full DB는 48 tables로 분리돼 있다.
- 운영 DB 읽기 전용 집계에서 기존 Kairos 발급 이력 6개를 확인하여 조건부 운영 교체를 **보류**했다.
  온라인 snapshot은 생성·gzip 검사 후 network-none MySQL 실제 복원까지 통과했다.
  복원 감사 container의 `running=false`를 최종 재확인했고 volume을 보존했다.
  최종 write-paused/전체 앱 복구는 별도이며 운영 대체는 실행하지 않았다.

### 동일 상태의 제한된 재개 계약

[SuiPersistentWorkflowResumeRunner](../src/test/java/com/api/trekkey/domain/credential/integration/SuiPersistentWorkflowResumeRunner.java)는
위 실패 실행의 정확한 두 digest와 PARTICIPATION/WORK ID를 대상으로 한다. 기존 보호 mount/env에
`SUI_FULL_RESUME=two-confirmed-anchors`를 추가해야 하며, fresh fixture 생성이나 기존 승인 갱신으로 fallback하지 않는다.

1. 원래 run.claim·fixture·3 credential(ANCHORED 2/READY AWARD 1)·2 batch·2 CONFIRMED transaction·2 PROCESSED outbox·status event 0을 대조한다.
2. 먼저 기존 두 public JSON/ZIP/PDF를 **읽기만** 하여 모두 VALID와 Sui proof가 맞아야 진행한다.
3. 새 `resume-two-anchors.claim`을 EXCL/fsync하고 기존 AWARD의 배치 1개 + PARTICIPATION 폐기 1개만 허용한다.
   추가 최대 예산 0.1 testSUI, 기존 2건 포함 총 4건/0.2 testSUI 상한을 유지한다.
4. 최종 기존 두 signed envelope/digest와 세 credential canonical/content hash가 그대로인지 검사하고 result.json을 남긴다.
   중간 실패면 원래/재개 claim과 새 단계도 보존하며 이 resume 역시 자동 반복하지 않는다.

재개 runner 컴파일 및 무효 gate NOT_RUN을 로컬 확인했고, **v6 적용 후 실제 제한 재개 1/1 PASS**도 확인했다.
원래/재개 claim과 최종 result를 보존하며 성공 후에도 이 runner를 반복하지 않는다.
재시작 후 별도 HTTP·브라우저의 증명서 상태와 업무 readback 8 checks도 완료했다.

이번 격리 통합의 지정 수락 기준은 충족했다. 전체 화면/학사 정책 전수 검증·mainnet 운영·Walrus/Seal 구현이나
legacy 체인 보존 방안이 필요한 운영 교체까지 완료한 것으로 확대 해석하지 않는다.
