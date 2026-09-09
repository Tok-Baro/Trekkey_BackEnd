# Sui 테스트넷 서버 격리 실행

2026-09-08. 이 문서는 **별도 테스트 배포**의 실행 계약이다. 기존 운영 컨테이너·DB·파일·Kaia 설정은 변경하지 않는다.
실행 전 실제 아티팩트/이미지 digest와 서버 상태를 기록한다. 이 문서 작성 및 오프라인 테스트가 원격 실행 성공을 의미하지 않는다.

후속 최신 `0615984` 통합과 `trekkey_sui_full` 검증은 [전체 통합 기록](./sui-full-integration-2026-09-08.md)을 우선한다.
아래 초기 `e113959` JAR·30-table 테스트 DB·H2 lifecycle 결과는 역사적 단계이며 최신 전체 기능의 검증 결과와 다르다.
사용자의 별도 승인으로 운영 백업·집계 조회·네트워크 차단 복원 시험은 수행했다. 운영 이미지·DB·기존 공개 검증은 교체하지 않았다.

## 1. 경계와 중단 조건

- Compose 파일: `infra/testnet/docker-compose.yml`, 프로젝트 이름 `trekkey-sui-testnet`.
- 이 격리 실행 도구에는 기존 운영 `.env`·relayer 키·MySQL 볼륨·uploads를 연결하지 않는다.
  별도 승인된 백업 도구만 서버 내부 root 보호 경로에 운영 자료를 보관하며, 운영 비밀과 원문을 로컬 소스나 로그로 가져오지 않는다.
- Java/gateway만 Linux host network 사용: Java `127.0.0.1:18080`, gateway `127.0.0.1:9187`.
  초기 schema bootstrap만 `127.0.0.1:18081`. MySQL은 별도 bridge에서 `127.0.0.1:13306`에만 publish한다.
- host network는 Java가 실제 loopback gateway에 접근하기 위한 선택이며 **네트워크 샌드박스가 아니다**.
  호스트의 다른 loopback 서비스에 연결할 수 있으므로 이미지 신뢰·전용 DB 사용자·인증 토큰이 필요하다.
- 세 포트와 18081이 사용 중이거나 운영 서비스의 메모리/디스크 여유가 부족하면 중단한다. 운영 서비스를 멈춰 자리를 만들지 않는다.
- Java 1.5GiB/1CPU, MySQL 1GiB/1CPU, gateway 512MiB/0.5CPU 제한. Docker 빌드의 자원은 런타임 제한 밖이므로 빌드도 모니터링한다.
- root filesystem은 read-only. Java/gateway는 UID/GID `10001`, capabilities 제거, no-new-privileges.
  MySQL 공식 entrypoint는 초기화 권한으로 시작하여 mysql 사용자로 내려가므로 동일 UID/cap-drop 정책을 강제로 적용하지 않았다.

## 2. 디렉터리와 비밀 계약

기본 기준 경로는 `/srv/trekkey-sui-testnet`다. 모든 디렉터리와 파일은 운영자가 **새 테스트용**으로 준비한다.
Compose의 bind mount는 없는 경로를 자동 생성하지 않는다. 비밀값을 Git, Docker build context, argv, 로그에 넣지 않는다.

| 상대 경로 | 내용 / 소유권 |
| --- | --- |
| `artifacts/app.jar` | 이번 Sui 변경 포함 Java 21 bootJar. 읽기 전용, SHA-256 별도 기록 |
| `secrets/deployment/deployment.json` | 배포 도구의 공개 metadata만. 10001 소유 0600, gateway에는 이 파일만 read-only bind |
| `secrets/deployment/relayer.key` | 배포 도구가 만든 canonical 전용 Ed25519 `suiprivkey...`, 10001 소유 0600. 복사하지 않고 파일 bind |
| `secrets/gateway/gateway-token` | 32~512자 공백 없는 printable ASCII 임의 토큰, 10001 소유 0600 |
| `secrets/backend/JWT_SECRET_KEY` | 테스트 전용 JWT secret, 10001 소유 0600. 기존 운영 JWT 재사용 금지 |
| `secrets/backend/EVIDENCE_LOOKUP_HMAC_SECRET` | JWT와 별도로 생성한 증빙 lookup HMAC secret, 10001 소유 0600 |
| `secrets/mysql/DATASOURCE_PASSWORD` | canonical 테스트 DB 사용자 비밀번호, 10001 소유 0600; MySQL root bootstrap과 Java에 같은 파일을 bind |
| `secrets/mysql/MYSQL_ROOT_PASSWORD` | 별도 테스트 MySQL root 비밀번호, root 소유 0600 |
| `journal/` | UID/GID10001, **0700**, gateway 준비/gas 예약 영속 데이터 |
| `uploads/` | UID/GID10001, 0700. 운영 uploads와 분리 |

deployment/gateway/backend secrets 부모 디렉터리는 UID10001 0700, mysql secrets는 root 0700으로 준비한다.
Java에는 DB 비밀번호/JWT/gateway-token/증빙 HMAC을 개별 bind하여 `/run/backend-secrets/{DATASOURCE_PASSWORD,JWT_SECRET_KEY,SUI_GATEWAY_TOKEN,EVIDENCE_LOOKUP_HMAC_SECRET}`으로 보인다.
gateway에는 relayer 키/토큰/공개 manifest 세 파일만 mount한다. 배포 도구의 deployer/issuer 비밀 파일이 있는 state directory 전체를 mount하지 않는다.
비밀 파일은 단일 hard link의 일반 파일이어야 한다. 심볼릭 링크·권한 불일치·manifest와 다른 relayer 키는 launcher가 거부한다.

배포 도구 원본 state directory의 `deployer.key`, `synthetic-issuer.key`는 gateway와 Java에 필요하지 않다.
`deployment.json`은 `network=testnet`, `protocolVersion=1`, `chainIdentifier`, `packageId`, `registryId`, `relayerAddress`를 포함해야 한다.
manifest의 IDs와 실제 RPC/Registry identity 검증을 통과해야 gateway가 listen한다. 승인 signer(secp256k1)와 relayer(Ed25519)는 다르다.

`deploy.env`는 **비밀 없는 Compose 입력 파일**로 아래 이름만 담는다. 실제 값은 검증한 manifest에서 복사한다.
`SUI_CHAIN_IDENTIFIER`는 8자리 소문자 hex, 두 ID는 `0x` + 64자리 hex다. 파일을 shell `source`하지 않는다.

```text
TESTNET_ROOT=/srv/trekkey-sui-testnet
TESTNET_FRONT_ORIGIN=http://localhost:3000
SUI_CHAIN_IDENTIFIER=<verified manifest value>
SUI_PACKAGE_ID=<verified manifest value>
SUI_REGISTRY_ID=<verified manifest value>
```

gateway의 Docker entrypoint만 파일→메모리 환경 주입을 담당한다. 기존 `npm start`는 여전히 `.env`를 자동 로드하지 않는다.
Java는 `configtree:/run/backend-secrets/`에서 네 비밀 파일을 읽는다. `docker compose config`에는 비밀값이 나오지 않지만
프로세스 메모리 및 Docker 관리자에 대한 방어는 아니다. 실제 서버에서 파일 소유권과 configtree 적용 여부를 반드시 검증한다.

## 3. 이미지와 기동 전 검증

### 비밀 파일·공개 설정 준비 도구

`infra/testnet/prepare-runtime.mjs`는 명시 실행한 경우에만 동작한다. Node 22 환경의 root 사용자로
`--init-secrets`, `--public-env`, `--add-evidence-secret` 중 하나를 지정한다. 증빙 HMAC은 최초 네 비밀 초기화와 독립적인 EXCL 생성이다.
기존 실행 경로에도 새 HMAC 파일이 없으면 추가한 뒤 Java를 기동한다. 기본 root는 `/srv/trekkey-sui-testnet`,
다른 격리 경로는 `--root /절대/trekkey-sui-testnet-이름`으로 지정한다. 경로의 basename은
`trekkey-sui-testnet` 또는 그 접두사의 전용 이름이어야 하며 심볼릭 링크·광범위 경로는 거부한다.
컨테이너로 실행한다면 준비된 전용 root를 **호스트와 같은 절대 경로에** mount해야 한다.

```sh
node infra/testnet/prepare-runtime.mjs --init-secrets
# deploy-testnet CLI가 완료하고 검증된 deployment.json을 저장한 뒤에만:
node infra/testnet/prepare-runtime.mjs --public-env
```

`--init-secrets`는 이미 준비된 root 소유의 전용 root 아래에 필요한 owner-only 디렉터리를 확인/생성하고,
서로 독립적인 gateway token/JWT base64/DB password/MySQL root password 4개만 만든다.
학교·relayer·deployer 키는 만들지 않는다. 기존 파일/claim은 덮어쓰거나 지우지 않으며,
파일과 부모 디렉터리를 fsync한다. 부분 실패면 claim/파일을 보존하고 운영자가 원인을 확인한다.
기존 디렉터리의 소유권/권한이 계약과 다르면 자동 수정하지 않고 거부한다.

`--public-env`는 canonical `secrets/deployment/deployment.json`만 읽고 testnet/synthetic/protocol/ID 및
genesis-prefix 일치/세 phase digest의 형식을 검사한 뒤 **4개 허용 필드만** 새 `deploy.env`에 기록한다.
이는 오프라인 구조 검사이며 실제 RPC 배포 검증을 대체하지 않는다. 어떤 모드도 비밀값을 출력하지 않는다.
오프라인 검사는 `node --test infra/testnet/prepare-runtime.test.mjs`로 실행한다(합성 입력만 사용).

아래 명령은 **승인된 배포 담당자가** 백엔드 저장소 경로에서 실행한다. 호스트에 Java/Node 설치는 필요 없다.
기존 production 이미지/JAR는 이번 미커밋 변경을 포함하지 않는다. 검증한 bootJar만 `artifacts/app.jar`에 배치한다.

```sh
docker compose --env-file /srv/trekkey-sui-testnet/deploy.env -f infra/testnet/docker-compose.yml config --quiet
docker compose --env-file /srv/trekkey-sui-testnet/deploy.env -f infra/testnet/docker-compose.yml build gateway
docker compose --env-file /srv/trekkey-sui-testnet/deploy.env -f infra/testnet/docker-compose.yml pull mysql backend
docker compose --env-file /srv/trekkey-sui-testnet/deploy.env -f infra/testnet/docker-compose.yml up -d mysql gateway
```

Node는 공식 ARM64 지원 `22.23.2-bookworm-slim`, Java는 `21.0.12_8-jre-jammy` release tag로 지정했다.
MySQL은 요구된 8.4 라인이다. 태그는 이미지 digest 고정과 같지 않으므로 실제 pull된 digest를 실행 기록에 남긴다.
근거: [공식 Node 이미지 목록](https://github.com/docker-library/official-images/blob/master/library/node),
[공식 Temurin 이미지](https://hub.docker.com/_/eclipse-temurin/tags?name=21).

gateway 미인증 요청은 401이어야 한다. 토큰을 argv에 넣지 않는 검증 도구로 `/v1/identity`를 호출하고 manifest와 일치하는지 확인한다.
gateway 시작은 조회/identity 확인만 수행한다. 준비 API는 서명할 수 있으므로 토큰을 일반 frontend에 전달하지 않는다.

## 4. 새로운 빈 DB 최초 bootstrap — 명시 실행만

일반 backend는 항상 `ddl-auto=validate`, `READ_ONLY`, worker=false다. 빈 DB에서는 validate 실패가 정상이다.
`READ_ONLY`는 체인 전송 정책일 뿐 일반 업무 DB 쓰기를 막는 모드가 아니다.

테스트 MySQL 볼륨은 `trekkey-sui-testnet_testnet-mysql-data`, DB/user는 `trekkey_sui_testnet`로 분리되어 있다.
bootstrap 전 **이 이름의 볼륨이 이번 작업에서 처음 생성됐고 해당 schema의 테이블 수가 0**임을 SQL로 확인한다.
이미 데이터가 있으면 아래 절차를 쓰지 않는다. 승인된 migration/복원 사본 검증을 별도로 진행한다.

빈 schema 확인 후에만 아래 명시적 profile을 실행한다. 일반 `up`은 profile을 활성화하지 않는다.
bootstrap은 현재 애플리케이션을 별도 18081 포트에서 `ddl-auto=update`로 실행한다. 성공 시작 로그와 생성된 schema를 확인한 뒤
`Ctrl-C`로 정상 종료한다. **자동 종료하는 migration 도구가 아니며 계속 켜두지 않는다.**

```sh
SUI_TESTNET_BOOTSTRAP_EMPTY_SCHEMA=I_VERIFIED_NEW_EMPTY_TESTNET_SCHEMA docker compose --env-file /srv/trekkey-sui-testnet/deploy.env -f infra/testnet/docker-compose.yml --profile bootstrap run --rm --no-deps backend-bootstrap
docker compose --env-file /srv/trekkey-sui-testnet/deploy.env -f infra/testnet/docker-compose.yml up -d backend
```

후속 일반 backend 기동의 schema validate 성공을 확인한다. 실패했다고 `validate`를 `update`로 바꾸지 않는다.
기존 운영 DB 대상 수동 migration SQL은 이 fresh-schema 절차에서 실행하지 않는다.
해당 SQL은 DDL auto-commit과 역사 행 UPDATE를 포함하며, 저장소 백업은 DB 백업이 아니다.

## 5. 확인·중단·복구

- 운영 backend/mysql의 container ID·health·8080 응답과 테스트 시작 전후 CPU/메모리/디스크를 비교한다.
- 테스트 Java 18080, gateway 9187, MySQL 13306이 모두 loopback인지 실제 socket으로 확인한다. 외부 security group은 열지 않는다.
- 새 DB에 운영 계정/개인정보를 가져오지 않는다. 필요한 demo 데이터는 명시적인 합성 seed 절차로만 만든다.
- 초기 HTTP/DB/identity 검증만으로 발급 E2E를 완료했다고 보고하지 않는다. `LOCAL_RELAYER`/worker 활성화 및 실제 testnet 제출은
  별도 승인·새 기관 키/조직 연결·outbox/journal 백업 후 별도 override로 진행한다. 기본 compose는 자동 활성화하지 않는다.
- 종료는 이 파일의 `stop backend gateway mysql`로 제한한다. `down -v`, Docker prune, 운영 compose down을 쓰지 않는다.
- journal의 BUILDING/UNSIGNED/SIGNED나 gas reservation을 TTL로 삭제하지 않는다. DB outbox와 함께 보존하고 동일 digest를 대조한다.
- 이번 변경에는 운영 자동 재시작을 넣지 않았다. 재부팅 뒤 수동 identity/config/schema 검증 후 테스트 서비스만 다시 시작한다.

## 6. 검증 기록 구분

이 파일을 만든 단계에서 TypeScript build, 합성 파일 기반 launcher 회귀, shell syntax, Compose 설정 파싱을 검사한다.
원격 ARM64 이미지 빌드, MySQL 초기화/validate, configtree 및 readonly-rootfs 실제 기동은 배포 담당자의 별도 실행 결과를 기록해야 한다.
전체 chain localnet 검증과 이 서버 testnet 검증은 서로 다른 증거다. 운영 준비 또는 mainnet 쓰기 허용으로 해석하지 않는다.

## 7. 2026-09-08 승인 전 준비 기록

- 사용자 요청에 따라 Notion의 기존 Trekkey EC2 제어 항목으로 서버를 시작했고, `running` 및 기존 공개 조직 조회 API의 HTTP 200을 확인했다.
- 기존 호스트 키 검증을 유지한 SSH 접속 성공. 기존 운영 컨테이너 2개는 계속 실행 중이며 재시작·교체하지 않았다.
- 운영 이미지 revision은 `0615984797b7c1c6b51f2f9e7ae7ca41332ae15c`, 이번 로컬 변경의 기준은 `e113959`다.
  운영 코드가 더 최신이므로 이번 미커밋 snapshot을 운영에 덮어쓰지 않는다.
- ARM64/Docker 환경과 여유 자원을 확인하고 `/srv/trekkey-sui-testnet` 아래 빈 artifacts/source/secrets/journal/uploads 디렉터리만 준비했다.
  새 키·비밀번호·DB·컨테이너는 아직 만들지 않았다.
- gateway 전체 회귀 **51/51 통과**, TypeScript build 통과. 배포 CLI의 8개 및 launcher의 3개 회귀가 포함된다.
- Java opt-in E2E와 전용 standalone runner를 추가했다. 실제 테스트넷 gate를 끈 실행은 **1개 skip, 실패 0**이며 실제 발급·취소 성공으로 계산하지 않는다.
- 기존 프론트는 백업 복원본과 다시 비교하여 내용 일치를 확인했다. 기존 사용자 README 변경 이외의 수정은 없다.
- 공식 테스트넷 RPC 읽기에서 network `testnet`, 전체 genesis `69WiPg3DAQiwdxfncX6wYQ2siKwAe6L9BZthQea3JNMD`를 확인했다. 배포 시 다시 확인해야 한다.
- 생산 Move JSON은 modules 2 / dependencies 2이며 SHA-256 `b9c5957b3bac1f1e264ff81241db5759828940f877cb26edb7c2719a79f765f1`.
  현재 bootJar SHA-256은 `a44835dcc26fcc05f69bf42835e1cecd4fbf1bb8f0ff3614f93b6e2e460dfb0d`다.
- **내부 소스 및 산출물의 EC2 전송은 보안 검토가 명시적 사용자 승인을 요구하여 차단됐다.**
  다른 도구나 경로로 우회하지 않았으며 실제 업로드·Docker 빌드·체인 publish·testnet E2E는 미실행이다.

후속 승인은 기존 사용자 소유 EC2의 위 테스트 전용 경로로, 허용목록 gateway 소스·Move bytecode·Java JAR·테스트 실행 묶음을
전송하는 범위를 명시한다. 비밀 설정/키와 원본 전체 백업은 전송하지 않는다. 승인 후에도 운영 서비스와 DB는 그대로 유지한다.

## 8. 2026-09-08 승인 후 실제 실행 결과

사용자가 위 목적지와 산출물 전송·격리 Sui 테스트를 명시적으로 승인하여 실행했다. **운영 교체는 하지 않았다.**
공개 좌표와 증거는 [실행 기록 JSON](deployments/sui-testnet-2026-09-08.json)에 있다. 이 JSON은 증거용이며
거래별 checkpoint를 담으므로, 서버의 배포 도구 전용 `deployment.json`을 대신하는 입력 파일은 아니다.

### 서버와 격리

- 승인한 gateway 소스·Move JSON·Java JAR·테스트 실행 묶음만 전송하고 각 SHA-256을 확인했다.
  사용자 지갑·기존 운영 비밀·전체 백업은 전송하지 않았다.
- 서버 안에서 새 배포자/relayer/합성 issuer 키와 테스트 토큰/JWT/DB 비밀번호를 생성했다. 값은 로그·argv·문서에 출력하지 않았다.
- Compose `tmpfs`의 쉼표가 YAML 배열 요소로 분리되는 문제를 실행 전에 문자열 인용으로 수정했다.
  MySQL root 접속 대상도 `localhost`로 제한했다. 실제 read-only-rootfs MySQL 8.4.11 초기화 성공.
- 새 전용 볼륨 생성 및 schema 테이블 **0개**를 먼저 확인했다. bootstrap으로 **30개** 생성 후 HTTP 200을 확인하고 bootstrap을 정상 종료했다.
- 일반 테스트 backend는 `ddl-auto=validate`, `READ_ONLY`, worker=false로 시작해 HTTP 200 확인.
  스키마 검증을 통과하기 위해 `update`로 완화하지 않았다.
- 실행 중: gateway `127.0.0.1:9187`, MySQL `127.0.0.1:13306`, Java loopback `18080`.
  bootstrap `18081`은 종료됐고, 중단된 bootstrap 컨테이너는 로그 보존을 위해 남겼다.
- gateway 미인증 identity 요청 **401**. 실제 E2E의 인증·identity·issuer 조회는 성공.
- 기존 운영 backend `62d804a4e4b2` / MySQL `d5dc8757e07b`는 교체·재시작 없이 유지됐고, 운영 API는 최종 HTTP 200이다.

실행 이미지 digest:

| 용도 | digest |
| --- | --- |
| Node 공식 base 22.23.2 | `sha256:83f487e0a63425e5b4d146fb5e5be574bcbe1b7b843d3ebafdd95eaf7767a7e5` |
| 최종 gateway 이미지 | `sha256:43d1a3720a99b67046fc2d8f6bb1ddecbaec44798f14820f7e0bd812cf17a3f1` |
| Java 서비스 JRE 21.0.12 | `sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf` |
| Java E2E JDK | `sha256:55fb9bf738f5d9b4a6c01b39337e3070d3e27370dd3c478fd1d5d3cd2233c6d8` |
| MySQL 8.4 | `sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb` |

### 실제 공개 테스트넷

- 공식 faucet UI로 새 deployer/relayer에 각각 무료 테스트 SUI 1개 수령, 공식 RPC 잔액으로 확인. 사용자 Slush 지갑은 사용하지 않았다.
- package publish → Registry 생성 → relayer/합성 issuer 등록 3건 모두 성공. 최종 Registry/issuer/relayer BCS 및 cap 소유권 확인.
- 최초 최종 검사에서 도구가 UpgradeCap 초기 버전을 `0`으로 기대해 중단했다. 고정 공식 Rust/Move 생성 코드와
  실제 공개 객체의 `version=1, policy=0`을 확인하여 수정·회귀 추가했다. 기존 3개 digest를 조회해 재개했고 중복 publish/Registry 생성은 하지 않았다.
- 합성 기관·증명서, 격리 H2, 실제 Java 관리자 JWT/API·발급 서비스·outbox·gateway·Sui testnet을 사용한 E2E **1/1 PASS, skip 0**.
  자동 worker는 꺼둔 채 테스트가 수동 worker만 구동했다.
- 공개 컨트롤러에서 Merkle/내용/기관/체인 증거 일치 및 `VALID` → `REVOKED`를 확인했다.
  두 거래 모두 DB `CONFIRMED`, Sui checkpoint 기록을 확인했다.
  - 발급: `5fLs2ngYYCRCmeiTK7dXaVQe3GBLKfwSzKwFXi7GgGCV`, checkpoint **381215064**.
  - 취소: `9gXxS9Ciw5ZH73wgAHrWjE2GDMcvkEmJh9UyEgbWx3AW`, checkpoint **381215106**.
- 공식 RPC로 배포 3건+E2E 2건의 성공을 독립 재확인했다. 크롬 Suiscan에서도 취소의 `Success`, `revoke_credential`, 동일 checkpoint를 확인했다.
- 최종 gateway 전체 회귀 **53/53**, TypeScript build, runtime 준비 도구 합성 회귀 **4/4** 통과.

### 완료 범위와 남은 경계

테스트 서버 3개는 켜둔다. Sui 상태·키·journal은 전용 서버 디스크에 유지한다. 재부팅 후에는 수동 검증·기동이 필요하다.
E2E의 증명서 DB는 일회성 H2였으므로 그 공개 증명서 URL이 상시 MySQL 서버에 남는 것은 아니다. 체인 거래와 journal은 보존된다.
상시 MySQL의 전체 발급 lifecycle, 기존 MySQL 데이터 upgrade/rollback, 장애복구·오프사이트 키 백업, 실제 기관 승인/키 관리는 별도 검증 대상이다.
프론트 파일은 수정하지 않았고 브라우저의 Trekkey UI 전체 E2E는 미실행이다. 기존 Kaia 표기 등 프론트 제한은 그대로다.
이번 결과는 **격리 테스트넷 이식 검증 성공**이며 운영 전환·mainnet 배포 완료를 뜻하지 않는다.
