# 기존 기능 완성·회귀 검증 (2026-09-08)

후속 전체 백업·복원본 migration·CI/CD 수정과 현재 배포 중단 상태는 [Sui 서비스 전환 준비 기록](./sui-service-release-2026-09-09.md)을 따른다. 아래는 기능 보완 직후의 이력이다.

상태: 기존 기능 보완 및 로컬 회귀 완료. 신규 산출물은 미커밋·미배포이며, 운영 대체 완료가 아니다. 이전 v6 격리 서버 결과와 이번 변경 결과를 혼합하지 않는다.

## 변경 계약

사용자 요청은 기존 기능 전반 보완·구현·테스트이며, 화면 디자인은 유지한다. 로컬 변경 전 두 저장소의 Git 이력과 미커밋 파일을 비공개 백업했다. 운영 전환은 백업·기존 데이터 호환·신규 기능 검증을 모두 통과한 경우에만 허용한다.

| 범위 | 완료 조건 |
| --- | --- |
| 혼합 체인 공개 검증 | Sui 활성 쓰기 설정에서도 저장된 Kaia 증명은 명시적 읽기 전용 allowlist로 검증. 모호한 좌표·알 수 없는 체인·RPC 실패는 검증 성공으로 처리하지 않음 |
| 공개 검증·Tamper Lab·승인 UI | 실제 체인 메타데이터와 승인 scheme으로 분기. Sui 승인을 Slush Ed25519 서명이나 EIP-712로 오인하지 않음 |
| 수동 제출 | 관리자 기관·대회·팀 소유권, 제출 기간·상태·파일 검증, 중복 409, 감사 기록, 오류/성공 UI |
| 심사위원 수정 | 이름·역할만 수정. 배정 후 수정 차단. 계정 변경이나 기존 심사 기록 변경 없음 |
| 구형 심사 링크 | 대회와 capability fragment를 보존. query token은 fragment로 이동 |
| 졸업 자가점검 | 적용 정책과 누락 영역을 함께 노출. 부분 정책 충족을 전체 졸업 충족으로 표시하지 않음. 항목별 실제 미충족은 보존 |
| 공개 정보·다운로드 | canonical payload는 유지하고 공개 subjectRef만 증명별 alias로 투영. 발급 시 PUBLIC과 현재 동의를 구분. 손상된 데이터도 ZIP/PDF 오류 없이 상태 표시 |
| Swagger | 공개 API는 security 빈 배열, 관리자 JWT, 심사 body token·refresh cookie 계약을 실제 필터와 대조 |
| 검증·문서 | 좁은 회귀 → 전체 Java/프론트 → 실제 UI·격리 서버. 실행하지 않은 범위와 조건부 skip 별도 표기 |

### 졸업 판정의 안전 범위

정책 종류·단위별 적용 범위와 입력의 완전성을 표시한다. 현재 모델에는 검증된 학적 상태가 없으므로 전체 충족(`ELIGIBLE`)을 확정하지 않는다. 알려진 필수 요건 미충족은 `NOT_ELIGIBLE`로 보존하며, 그 외 부분 coverage는 `INDETERMINATE`다. 이 변경은 대학 공식 기준값을 새로 만들거나 기존 정책의 정확성을 인증하지 않는다. 평가 snapshot에는 사용한 입력과 coverage를 보존한다.

## 이번 변경에서 완료로 주장하지 않는 범위

전체 학과·트랙·입학연도 공식 정책, 실제 학적 연동, 기관 직접 증빙 검증, 졸업·교과목·자격증 신규 Credential schema, Walrus/Seal 실배포, 메인넷·운영 키 소유권 절차는 별도 근거와 권한이 필요하다. 기존 증명의 canonical payload·서명·해시·앵커를 재발급하거나 수정하지 않는다.

## 변경 전 백업

- 로컬 비공개 archive: `backups/completion-20260908.gJIf1u/before-completion.tar.gz` (상위 workspace 기준)
- SHA-256: `9b6fd2c13bc15d6798edc15647b0a9ca9cd425d8a10c3e11ba8e68a90f6f1d3d`
- 저장소와 설정을 포함하므로 외부 업로드·공개 금지. 기존 v6 서버/DB/증명과 이전 백업 보존.

## 이번 실행 결과

| 검사 | 이번 실행 결과 | 범위와 제한 |
| --- | --- | --- |
| 변경 전 백업 | gzip 무결성 PASS | 위 archive 39,561,490 bytes, 파일 0600·상위 디렉터리 0700. 외부 전송하지 않음 |
| 졸업 판정 red regression | 수정 전 실패 재현 → 수정 후 통과 | 공통 정책만 충족한 입력에 전체 ELIGIBLE을 반환하던 오류 |
| 전체 Java + bootJar | **123 suites, 842 tests: 813 PASS / 29 SKIP / 0 FAIL** | `--offline test bootJar --rerun-tasks`; 실제 합성 PDF를 사용하는 H2 HTTP 증빙 테스트 포함 |
| 관리자 신규 기능 HTTP | **18/18 PASS** | JWT·기관 경계·대회/팀·기간/잠금·파일·중복·심사위원 수정·감사 기록. 전체 Java에 포함 |
| 혼합 체인 좁은 회귀 | **66/66 PASS** | 체인/RPC/runtime hash 불일치·빈 코드·읽기 실패 주입 포함. 실제 운영 Kaia 읽기 전환은 미실행 |
| Swagger 실제 HTTP 계약 | **4/4 PASS** | 실제 `/v3/api-docs` 응답과 Spring Security 필터. 전체 Java에 포함 |
| 프론트 테스트 + Vite build | **72/72 PASS**, build PASS | Sui/Kaia 분기·승인 digest·JSX 공개 화면·폼·API multipart·심사 링크·졸업 coverage 회귀 |
| 격리 서버 검증 도구 합성 테스트 | **30/30 PASS** | 신규 completion 도구 11개 포함. 네트워크·로그인·DB·체인을 호출하지 않은 오프라인 검사 |
| PDF 렌더링 | **3/3 시각 확인 PASS** | VALID·REVOKED·TAMPERED 각각 1쪽, 한글/상태/배치 확인. 11개 상태 전체는 자동 회귀로 확인 |
| 문서·코드 정합성 | **35 문서 / 33 routes / 108 operations PASS** | working tree exact-set·로컬 링크·secret pattern 검사, reader 재생성. Chrome에서 이번 보고서 제목·상태·목차·표 렌더링 확인. 두 저장소 diff whitespace 검사 PASS |
| Chrome 실제 공개 화면 | AWARD 조회와 Tamper Lab 브라우저 proof 재계산 PASS | **새 프론트 + 이전 v6 백엔드** 조합. Sui testnet·checkpoint·registry·승인 scheme 표시 확인. 새 백엔드의 원격 검증 아님 |
| 원격 preflight | 읽기 전용 확인 PASS | 기존 v6/운영 컨테이너 가동, 디스크·메모리 여유, 운영 기관 조회 HTTP 200. 신규 배포/DB 변경 없음 |
| 신규 v7 원격 HTTP·재시작·DB 검사 | **미실행** | 내부 JAR·실행 스크립트의 EC2 전송이 승인 검토에서 실행 전에 거절됨 |

29 SKIP은 MySQL opt-in 26개, Sui opt-in 2개, 별도 외부 파일 smoke 1개다. 이를 통과로 계산하지 않는다. 기존 v6의 MySQL 25개 및 Move/Gateway 테스트 기록은 역사적 결과이며, 이번 변경에 대한 신규 MySQL 26개 실행을 대신하지 않는다.

첫 전체 실행은 838개 중 54개가 테스트 JVM 환경변수 누락으로 실패했다. `build.gradle`의 Test task에만 합성 JWT/HMAC·만료시간·localhost URL·build 아래 업로드 경로 기본값을 추가했다. 명시적 환경변수와 Spring 테스트 override를 보존하며, 애플리케이션 실행·DB/체인 opt-in 설정은 바꾸지 않았다. 이후 해당 54개와 환경 회귀 3개가 통과했고, 위 전체 842개를 재실행해 실패 0을 확인했다.

### 주요 변경의 의미

- 관리자 수동 제출과 심사위원 수정은 실제 API·감사 기록까지 연결했다. 심사위원의 기존 계정/심사 기록은 수정하지 않으며 배정이 있으면 거절한다. 제출 제목은 실제 multipart body에 담는다. 중복 클릭, 실패 후 입력 보존, 오래된 요청 결과로 화면이 덮이는 경우도 검사했다.
- 공개 `subjectRef`는 `public-subject:<credentialPublicId>:<publicIndex>`다. 내부 숫자 PK나 증명 간 공통 식별자를 내보내지 않지만, PUBLIC 이름·학과 등은 계속 노출될 수 있다. 이는 익명화나 현재 동의 확인 기능이 아니다. 원본 canonical bytes·hash·서명은 바꾸지 않았다.
- 다운로드 disclosure는 `ISSUANCE_PUBLIC_SUMMARY`로 명시한다. 발급 시 공개 요약이라는 의미이며 현재 동의/철회 상태를 인증하지 않는다. PDF는 검증기의 공개 projection만 사용하고, VALID 외에는 수여 증서 대신 현재 상태 확인서를 출력한다. null/손상된 발급 정보도 안전하게 처리한다.
- Sui 활성 설정과 레거시 Kaia 검증 읽기를 분리했다. 레거시 읽기는 명시적 allowlist·chain ID·runtime hash·저장된 좌표 일치가 필수다. **레거시 쓰기 전환은 구현하지 않았다.**
- Sui 승인은 `TREKKEY_SUI_APPROVAL_V1`의 recoverable secp256k1 서명이며 Slush의 Ed25519 트랜잭션 서명과 다르다. Java/Move 고정 벡터 및 Kaia EIP-712 벡터로 분기를 검사했다.
- 졸업 snapshot은 적용 정책/coverage뿐 아니라 입력·전공 단위·교과목 식별자/배분 영역·비교과 근거를 보존한다. 공식 학적과 전체 정책의 검증 없이는 전체 충족을 주장하지 않는다.

### 로컬 산출물 (전송하지 않음)

| 파일 | SHA-256 |
| --- | --- |
| `build/libs/trekkey-0.0.1-SNAPSHOT.jar` | `de62b9dbe665e23898fbff8e0ff50573bdfa34e843ca8a850fe2d5b87d76d82e` |
| `/private/tmp/trekkey-completion.eVCZK7/e2e-v7.tar.gz` | `49812200a6779baee6ab113d8508a20c582ff2894c1ce46b91c4f76f258e8c9c` |

임시 회귀 bundle에는 컴파일된 main/test와 테스트 runtime 의존성이 들어간다. 키·운영 설정·원본 백업의 전송은 허용하지 않는다. 임시 경로는 영구 보관 위치가 아니므로 후속 실행 시 SHA를 다시 확인해야 한다.

`infra/testnet/verify-completion-http.mjs`는 추후 승인된 격리 실행을 위해 준비했다. 고정 localhost origin/전용 DB/기존 fixture만 허용하고, 기존 8개 읽기 검사에 Swagger·학생 관리자 API 거절 2개·부분 coverage 평가를 더해 12개 HTTP 결과를 검사한다. 합성 학생 로그인 1회와 평가 snapshot 1회만 허용하며 새 대회·파일·기관 키·체인 거래는 만들지 않는다. 기존 결과를 덮어쓰지 않는 전용 `completion-v7.claim`/`completion-v7-result.json`을 사용한다. **이 12개 실제 HTTP 검사는 아직 실행하지 않았다.**

## GitHub CI/CD 확인 (읽기 전용, 2026-09-08)

- 백엔드 GitHub main `0615984`에는 `Backend CI`와 `Backend CD`가 있다. CI는 develop/main PR·push의 Java/MySQL 및 Solidity 검사를 수행한다. main CI 성공 뒤 검증된 SHA의 ARM64 이미지를 GHCR에 올리고 EC2 production을 교체한다. [최근 CI 성공](https://github.com/Tok-Baro/Trekkey_BackEnd/actions/runs/32697835709) 및 [최근 CD 성공](https://github.com/Tok-Baro/Trekkey_BackEnd/actions/runs/32698147533)은 **2026-08-24** 실행이며 이번 로컬 변경이 아니다.
- 현재 CD는 `BLOCKCHAIN_CHAIN_ID=1001`, `LOCAL_RELAYER`, worker=true를 강제하며 `/etc/trekkey/relayer.env`를 요구한다. 운영 Compose에 Sui profile·gateway·전용 secret mount 설정이 없다. 로컬 CD와 GitHub main CD의 SHA-256이 동일함을 확인했다. 따라서 현재 파이프라인에 이번 변경을 그대로 main push하는 것은 Sui 배포가 아니다.
- 프론트 main `946525e`에는 Vercel 성공 commit status가 있다. [해당 Vercel 배포](https://vercel.com/9hkmo-b9381330/trekkey/63DmUs1j3xAuD5cCTaY69VwyCBRN). `Frontend CI`는 과거 2026-07-26 PR 실행이 등록돼 있으나, **현재 main tree에는 `.github/workflows/` 파일이 없고 현재 commit check-runs도 0개**다. 과거 등록 상태를 현재 프론트 테스트 CI 존재로 간주하지 않는다.
- 이번 작업에서 workflow를 수정하거나 commit·push·Actions dispatch·Vercel/EC2 배포를 하지 않았다. 승인 거절된 직접 EC2 전송을 GitHub push로 우회하지 않는다. 기존 GitHub 경로를 사용할 경우에도 대상 브랜치·전송할 변경·실제 배포 범위를 확인한 별도 승인이 필요하다.

## 다음 단계와 운영 전환 조건

1. 사용자가 배포 경로와 범위를 승인하기 전에는 원격 쓰기를 하지 않는다. 기존 시도는 EC2 `43.200.222.11`의 `/srv/trekkey-sui-testnet/` 아래 신규 v7 JAR·런타임 업로드였고, 실행 전에 거절돼 서버 파일/컨테이너/DB에 변경이 없다.
2. GitHub 경로를 선택하면 Sui/레거시 읽기 설정·gateway·분리된 키 mount·테스트넷 staging 검증·수동 production gate를 기존 CI/CD에 맞춰 설계해야 한다. 현재의 Kaia production workflow를 바로 실행하지 않는다. 프론트 자동 테스트 CI도 현재 main 기준 보완 대상이다.
3. 승인된 격리 환경에서 신규 MySQL 회귀와 completion HTTP 검사를 실행하고 재시작 지속성을 확인한다. 이전 one-shot seed/체인 발행은 재실행하지 않는다.
4. 운영 DB 복원본에 schema/context migration을 적용해 기존 Kaia 증명 읽기를 검사해야 한다. 이전 백업 복원 성공은 이번 schema 업그레이드 검증과 다르다.
5. 실제 기관 Sui 키는 레거시 Kaia v1을 덮어쓰지 않고 사용하지 않은 새 keyVersion과 registry 승인을 받아야 한다. 기존 Kaia 증명에 대한 향후 쓰기/취소 정책도 별도 결정이 필요하다.

검증 방법은 `safe-change-delivery`의 계약·회귀 기준, `rigorous-product-planning`의 완료/비완료 범위 구분, `evidence-to-product`의 표시 근거 구분, `pdf`의 실제 렌더링 검사를 적용했다. 현재 결과는 **기능 보완·로컬 검증 완료, 원격/운영 전환 보류**다.
