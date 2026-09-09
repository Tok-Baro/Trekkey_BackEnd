# Sui 서비스 전환 준비·백업 기록

2026-09-08 시작, 2026-09-09 KST 기록. 사용자 요청: 전체 백업 후 기존 서비스를 Sui로 배포.
대상은 기존 EC2 `43.200.222.11`, 기존 GitHub `Tok-Baro/Trekkey_BackEnd`·`Tok-Baro/Trekkey`, 기존 Vercel 프론트다.
**Sui 테스트넷 전환**이며 mainnet 전환·실제 기관의 새 키 사용 승인을 뜻하지 않는다.

현재 상태: **목적지·페이로드별 사용자 승인 후 격리 v7 검증 진행 중, 운영 전환 전**.
이전 전송 거절 뒤 사용자가 기존 EC2와 두 GitHub 저장소의 전송·push·Sui 테스트넷 교체를 명시 승인했다.
그 승인에 따라 JAR·검증 번들을 전송하고 해시를 확인했다. 비밀키·전체 백업은 외부 전송하지 않았다.
기존 운영 앱·DB는 유지하며, 격리 앱만 v7으로 교체했다. 운영 DB migration은 아직 적용하지 않았다.

### 승인 후 확인된 결과

- v7 JAR와 회귀 번들 SHA-256 및 archive 경로 검사 PASS.
- 기존 Kaia 공개 증명 6개 baseline JSON capture PASS. EIP-55 주소 case 정규화 회귀 해결.
- 실제 격리 MySQL **9 classes / 26 tests / 26 PASS / 0 skip / 0 failure**.
- 운영 복원 DB를 `read_only`·`super_read_only`로 고정한 v7 앱 시작 PASS (`ddl-auto=validate`).
  기존 Kaia 공개 증명 6건의 **JSON 6 + PDF/ZIP 12 = HTTP 18/18 PASS**.
  기존 원문·체인 좌표·표시 내용 유지와 새 개인정보 보호 subject alias를 함께 확인했다.
- 격리 Sui v7 HTTP **12/12 PASS**: 영속 데이터 8개, 실제 Swagger 108개 API 계약,
  학생의 신규 관리자 API 접근 거부 2개, 학사 정보 부족 시 안전한 평가 응답.
  합성 학생 로그인·평가 snapshot 각 1회만 생성했으며 운영/체인 쓰기는 하지 않았다.
- 프론트 release branch SHA `4368a57e36a2c1bd2d5bbb7fbf6ef0e677597c37` push 완료.
  [실제 GitHub CI](https://github.com/Tok-Baro/Trekkey/actions/runs/34315930582): Node 22.23.2, **72/72 PASS·build PASS**.
  [자동 Preview](https://trekkey-b84ikwsfg-9hkmo-b9381330.vercel.app) 성공. 프론트 main은 아직 승격하지 않았다.
- GitHub production 공개 변수 `EC2_KNOWN_HOSTS`에 사전에 확인한 EC2 ed25519 키를 등록했다.
- root 전용 `deploy-service.sh`를 구현했다. 정확한 커밋·검증 증거·Compose/스크립트 해시를 승인 marker에 묶고,
  SHA 이미지의 ARM64/revision/source/digest 확인 → 쓰기 중단 → 최종 백업 → 원장 지문 대조·migration →
  앱·gateway 교체 → 실제 API/identity/legacy 18개 검사를 수행한다. MySQL 컨테이너는 재생성하지 않는다.
  실패 시 가능한 범위에서 구 앱만 복구하며, 구 앱의 자동 DDL은 `none`으로 강제한다.
  새 Sui 기록이 있으면 구 worker 재시작을 거부하고 데이터·백업을 보존한다. 실제 운영 실행은 아직 전이다.
- 복원 앱 실행기는 기존 artifact 상위 디렉터리의 ubuntu 소유/그룹 쓰기를 거부했다.
  실제 앱 생성 전 실패였으며, 원래 권한을 바꾸지 않고 root 전용 보호 경로의 동일 SHA JAR로 검증한다.

아래 준비 시점의 미실행·승인 차단 기록은 이력이다. 최신 결과는 위 목록 및 이후 전환 결과를 기준으로 한다.

## 백업

| 대상 | 보관 위치 / 검증 |
| --- | --- |
| 두 저장소 전체 소스·Git·미커밋/ignored 설정 | workspace `backups/sui-release-20260908.91aEkg/source-before-release.tar.gz`, gzip PASS, SHA-256 `3b6c969993ba17d26d76ca5416b58ac25c95d30ae2fb8d6a1b5ef042525b53af` |
| 운영/테스트 서버 전체 보존 | `/srv/trekkey-backups/sui-release-20260908-online`, 7개 archive gzip PASS, 약 2.2 GiB. `SHA256SUMS` 파일 자체 SHA-256 `9b3a21ab2c0e13aab99095eedc6eb916fb54a90e34d398e8a6a28a32271e9d91` |

로컬 백업은 재설치 가능한 node_modules·Gradle/build·컴파일 캐시를 제외한다. 서버 백업은 운영 `trekkey`,
초기 `trekkey_sui_testnet`, 영속 `trekkey_sui_full` DB dump 3개와 운영 설정·업로드·전체 테스트넷 상태(키/journal/산출물/파일/실행 이력),
현재 4종 서비스 이미지 archive를 포함한다. 보관 디렉터리 0700, 파일 0600이며 **비밀값/백업을 외부에 전송하지 않았다**.
온라인 백업이므로 실제 전환 직전 쓰기를 중지한 최종 백업은 별도 필요하다. 기존 모든 이전 백업도 보존한다.

## 실제로 완료한 서버 작업

1. 읽기 전용 운영 집계: 48 tables, ANCHORED AWARD 6개, batch 1개, CONFIRMED Kaia chainId 1001 거래 1개, PROCESSED outbox 1개, 기관 keyVersion 1 한 개.
2. 위 온라인 백업 7종 생성·압축 무결성 확인. 운영 서비스는 중지하지 않았다.
3. 새 네트워크 차단 MySQL에 운영 백업 복원 후 Sui migration 적용. **4개 nullable chain_context와 2개 VARBINARY(32) 계약 PASS**.
   batch/status에 다중 tx JOIN 모호성이 없는지 사전 검사했고, 기존 canonical bytes·manifest·payload·내용 hash·Merkle/approval·서명·주소·tx digest/nonce·키 lifecycle 지문이 전후 동일했다.
   결과: `/srv/trekkey-backups/sui-release-20260908-rehearsal-v2/result.json` PASS.
   컨테이너 `trekkey-sui-release-20260908-rehearsal-v2`는 정지, 같은 이름의 `-data` volume은 보존.
4. `/etc/trekkey/sui-runtime.env`와 `/etc/trekkey/sui-secrets/EVIDENCE_LOOKUP_HMAC_SECRET`을 새로 준비했다.
   기존 로그인/증빙 lookup의 유효 비밀을 바꾸지 않으며 HMAC의 기존 JWT fallback까지 보존한다.
   Sui manifest와 레거시 공개 좌표를 대조했다. 기존 `/etc/trekkey/trekkey.env`·`relayer.env`는 변경하지 않았고, 새 기관 private key도 만들지 않았다.
5. `/srv/trekkey-backups/sui-release-20260908-http/ids.json`에 공개 증명 6개의 UUID만 보호 저장했다.

첫 복원 시험은 SSH stdin으로 전달한 스크립트 안의 불필요한 `docker exec -i`가 나머지 스크립트 입력을 소비해 최종 결과 없이 끝났다.
이를 성공으로 계산하지 않았고 첫 복원 디렉터리/정지 컨테이너/volume을 보존했다. 스크립트를 설치된 파일로 실행하고 불필요한 `-i`를 제거한
새 v2 복원본에서 최종 marker와 원문 지문 대조까지 통과했다. 운영 데이터에 재실행한 것이 아니다.

기존 공개 HTTP 기준 저장은 검사기의 소문자-only 주소 정규식 때문에 실패했다. 실제 응답은 42자 EIP-55 대소문자 주소이며
chainId·거래 해시·block number 형식은 정상이다. 로컬 도구는 정확한 20-byte hex를 검증한 후 case만 정규화하도록 수정했고
동일 EIP-55/소문자 PASS·주소 한 자리 변이 FAIL 회귀를 추가했다. **수정된 도구의 서버 capture와 신규 앱 18개 HTTP 검증은 아직 미실행**이다.

## 코드·로컬 검증

- Java **123 suites, 851개 = 822 PASS / 29 SKIP / 실패 0**, bootJar PASS.
  신규 기존 Kaia 보호 회귀 8개와 MySQL 테스트 기대 개수 회귀 1개 포함.
  29 SKIP은 MySQL opt-in 26개, Sui opt-in 2개, 외부 파일 smoke 1개다. 아직 실제 MySQL 26개를 실행하지 않았다.
- 프론트 공식 체크섬 확인한 **Node 22.23.2/npm 10.9.8 clean-copy npm ci·72/72 테스트·build PASS**.
  `.env`는 복사하지 않고 기존 공개 API 원점만 주입했다. 현재 Vercel 환경변수의 실제값까지 검증한 것은 아니다.
- gateway **53/53 PASS·TypeScript build PASS**. 로컬 Node 25 실행이며 새 GitHub Node 22 실행은 미실행.
- infra testnet **30/30**, 새 legacy HTTP 도구 **11/11** 합성 PASS. 각 복원 SQL guard **11/11** PASS.
- 백엔드 CI에 release 브랜치 push·Sui gateway·infra/release 도구·스키마 가드·API 108개 정합성 검사를 추가했다.
  MySQL wrapper/runner/준비 metadata의 기대값을 실제 26개로 맞췄다. Move CLI의 검증된 Linux 설치 계약이 없어 임의 최신 설치를 넣지 않았다.
- 프론트 CI를 새로 추가했다. Node 22에서 npm ci → test → build, main/develop PR·push 및 `codex/sui-service-release-20260908` push와 수동 실행을 지원한다.
- CD 수정안은 같은 저장소 main push CI 성공과 정확한 SHA만 사용해 backend/gateway ARM64 이미지 두 개를 게시하고,
  고정 ed25519 호스트 키와 root 소유 helper에 정확한 SHA를 전달하도록 제한한다. 기존 비밀 설정 복사·Kaia 키 mount·latest/prune는 제거했다.
  **서버의 exact-SHA 승인·백업·migration·health/rollback을 담당하는 `deploy-service.sh`는 아직 작성/설치되지 않았다. CD 배포 준비 완료가 아니다.**
- 운영 Compose의 MySQL 서비스와 volume 선언은 기존 바이트 그대로 보존했다. Sui gateway는 backend의 network namespace에서
  loopback 9187만 사용하며 키/토큰은 read-only mount다. 이전 테스트 gateway와 동일 journal을 쓰므로 전환 때 반드시 단일 writer가 되게 해야 한다.

로컬 파일 검사는 실제 원격 CI/CD, 실제 앱의 Hibernate validate, 기존 Kaia 6개 공개/다운로드 유지 검증을 대신하지 않는다.
수정 전부터 있던 프론트 audit 26 moderate·1 high도 남아 있다. high는 Babel 빌드 경로의 transitive `browserslist@4.28.4`이며
이번에 잠금파일을 변경하지 않았다. 별도 의존성 업데이트·재검증이 필요하다.

### 이번 로컬 산출물

| 파일 | SHA-256 |
| --- | --- |
| `build/libs/trekkey-0.0.1-SNAPSHOT.jar` | `b59819b78bf87d7681c7ae65fb117433eea0774fd9032e0b539db37279d971f9` |
| `/private/tmp/trekkey-sui-release.7WviOm/e2e-v7.tar.gz` | `b00a6bbcd69a8b65f55716f1f0d00e8b3a71f783e4d05f99fa30583f08969641` |

새 JAR/회귀 번들/v7 경로 전송 명령은 승인 검토에서 **실행 전에 거절**됐다. 커밋·push·새 GitHub Actions 실행도 하지 않았다.
Git 후보의 비밀 패턴 검사에서 실제 키/토큰 후보는 없었지만 최종 staging 재검사는 계속 필요하다.
원래 untracked 출품 보고서 DOCX는 배포 대상에서 제외하며, Python cache와 env/키/backup은 `.gitignore`로 보호한다.

## 승인 후 남은 순서

1. 목적지 `43.200.222.11`의 전용 배포/검증 경로에 JAR·gateway 코드/이미지·회귀 bundle·배포/검증 스크립트 전송을 명시 승인받는다.
   기존 GitHub 두 저장소의 소스/CI 변경 push와 해당 호스트 자동 배포도 승인 범위에 포함한다. 비밀키와 전체 백업은 서버 밖 전송 대상이 아니다.
2. 수정된 baseline capture → 실제 MySQL 26개 → 복원본 새 앱 `ddl-auto=validate`·기존 6개 JSON/PDF/ZIP 18검사 → 격리 Sui HTTP 검사를 완료한다.
3. root 배포 helper와 exact-SHA release gate·이미지 digest/label 대조·부분 DDL 복구/rollback을 구현/시험한다.
   단순 이미지 rollback이 DB rollback이라는 가정은 하지 않는다. 필요한 모든 local gate를 통과한 커밋만 push한다.
4. 실제 GitHub CI 통과 후 전환 직전 write-paused 최종 DB/파일/journal 백업, 원장 지문 대조, 승인된 운영 migration을 수행한다.
   옛 gateway와 신규 sidecar를 동시에 켜지 않는다. 기존 앱 이미지/Compose/DB 백업은 보존한다.
5. 새 backend/gateway 배포와 기존 프론트 Vercel 배포 뒤 실제 origin·로그인/권한·공개 6개·Sui identity·다운로드·재시작 지속성을 검사한다.
6. 기존 기관 Sui 발급 활성화는 별도 사용하지 않은 keyVersion과 현재 유효한 signer에 대한 기관 사용 승인/registry 등록·DB sync가 필요하다.
   같은 공개 signer 재사용은 기술적으로 가능하지만 legacy 폐기/침해 상태나 기관 동의를 자동으로 우회할 수 없다.
   새 PRIVATE key/합성 issuer를 실제 기관에 대입하지 않는다. 기존 Kaia 쓰기/취소는 active Sui 설정에서 명시 차단된다.

`safe-change-delivery`의 계약·실제 백업/복원·운영 경계 기준으로 수행했다. 현재 보안 검토의 구체적 전송 승인 요구 때문에
서비스 교체를 멈췄으며, 로컬 검증 통과를 Sui 운영 전환 완료로 보고하지 않는다.
