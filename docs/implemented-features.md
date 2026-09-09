# Trekkey 구현 기능 카탈로그

- 기준일: 2026-09-08 KST
- 프론트엔드 기준: `Trekkey/main@946525e` + 전반 기능 보완 working tree. 디자인/CSS와 기존 dirty README 보존
- 백엔드 현재 HEAD·최신 통합 기준: `Trekkey_BackEnd/0615984` + Sui·통합 수정 및 기존 사용자 working-tree 변경
- 2026-08-30에는 `e113959` checkout을 변경하지 않고 `origin/main` 객체를 감사했다. 그때의 16커밋 지연은 현재 상태가 아니다.

이 문서는 Trekkey의 현재 기능 상태를 코드에서 다시 확인한 정본이다. 상세 설계가 존재한다는 이유만으로 구현 완료로 표시하지 않는다. 화면 경로는 [화면·라우트 명세](./spec/pages.md), 전체 HTTP surface는 [API 카탈로그](./spec/api-catalog.md), 남은 게이트는 [프로젝트 현황](./project-status.md)이 맡는다.

이전 v6 실행의 정본은 [전체 통합 검증·운영 인계](./sui-full-integration-2026-09-08.md)다. 당시 결과: Java **760개=733 PASS/27 SKIP/실패 0, 117 suites·22초·build PASS**, 별도 MySQL **9개 class·25/25 PASS**, 상시 HTTP **27 checks PASS**, 프론트 **29/29·build PASS**, 영속 Sui 동일 상태 resume **1/1 PASS**. 당시 최종 원장은 4 CONFIRMED·3 batches·1 status event·4 PROCESSED이며 PARTICIPATION REVOKED·WORK/AWARD VALID와 runner PDF/ZIP 검사를 통과했다. v6 격리 `18080` 재시작 후 독립 공개 HTTP·PDF/ZIP 6개·업무 readback **8 checks PASS**, Chrome 공개 결과 확인까지 완료했다. fresh infra **19/19 PASS**·SQL guard 11개 검사도 확인했다. 당시 기존 운영 Kaia 증명 6개의 검증 경로가 없어 단일 Sui 운영 교체는 보류했다.

첫 영속 실행의 WORK timestamp 반올림 TAMPERED 오탐은 실패 이력으로 보존한다. 최종 수정/재개에서 기존 두 거래의 digest·bytes와 canonical 원문이 바뀌지 않았음을 검사했으며 실패를 숨기기 위해 새 증명을 다시 만든 것이 아니다.

최신 후속 변경과 현재 테스트·배포 결과는 [기능 완성·회귀 검증](./completion-2026-09-08.md)을 우선한다. 현재 로컬 결과는 **Java 123 suites·842개=813 PASS/29 SKIP/실패 0·build PASS**, **프론트 72/72·build PASS**다. 새 JAR은 원격 전송·반영하지 않았다. 위 760/29 수치는 이전 v6 실행이다. 아래 `구현·회귀 완료`는 코드와 지정 회귀를 뜻하며 운영 배포·전체 브라우저 완료가 아니다.

## 상태 어휘

| 상태 | 의미 |
| --- | --- |
| `운영 화면` | 현재 화면 또는 실제 사용자 흐름이 구현되어 API와 연결됨 |
| `연구 화면` | 실험·검증·벤치마크 화면이 구현됐으나 운영 성능이나 사실 판정으로 일반화할 수 없음 |
| `제한 운영` | 구현됐지만 데이터·정책·배포·권한·릴리스 게이트가 남아 있음 |
| `내부 운영` | 관리자 도구, worker, 감사, 배포, 복구 등 사용자 외 운영 경로 |
| `구현·회귀 완료` | 현재 로컬 코드와 지정 회귀 검증 완료. 원격 반영·운영 릴리스·전체 브라우저 검증 완료를 뜻하지 않음 |
| `구현 전` | 설계나 계약만 있고 필요한 화면·API·테이블·worker가 완성되지 않음 |

## 기능 현황

### 1. 인증·권한·조직

| 기능 | 상태 | 현재 근거와 경계 |
| --- | --- | --- |
| 참가자 회원가입·로그인 | `운영 화면` | 이메일·비밀번호 가입, JWT access token, HttpOnly refresh cookie rotation과 로그아웃이 연결됨 |
| refresh token 재사용 탐지 | `내부 운영` | 폐기된 refresh 재사용 시 토큰 패밀리를 폐기하는 서비스와 테스트가 존재 |
| 로그인 실패 잠금 | `내부 운영` | 연속 실패 잠금과 `auth.login_locked` 감사 이벤트가 구현됨 |
| 관리자 초대·가입·승인 | `운영 화면` | `ROOT_ADMIN`이 초대를 발급하고 가입 대기 관리자를 승인·거절함 |
| 역할 계층 | `내부 운영` | `ROOT_ADMIN > ADMIN`; 참가자·관리자·최고관리자·심사 링크 권한을 분리 |
| 조직 검색 | `운영 화면` | 활성 학교 검색을 회원가입에서 사용 |

### 2. 공모전 운영

| 기능 | 상태 | 현재 근거와 경계 |
| --- | --- | --- |
| 대회 생성·조회·수정·단계 상태 | `운영 화면` | 관리자 CRUD, 참가자 검색·상세, 단계 상태 전환이 구현됨 |
| 참가 신청·팀 구성·팀원 검색 | `운영 화면` | 신청 생성·수정, 최대 팀원 수 검증, 관리자 승인·명단 확정이 연결됨. 승인된 프론트 수정으로 고정 5명 대신 `maxTeamMembers`에 맞춰 안내·검색/추가 상한·제출 검증을 일치시킴 |
| 작품 제출·재제출·다운로드 | `운영 화면` | 팀 대표자의 multipart 제출과 재제출 시 파일 교체, 역할별 다운로드가 구현됨 |
| 관리자 수동 제출 접수 | `구현·회귀 완료` | 실제 multipart 신규 접수 API 연결. 중복·기관/대회/팀 교차·기간·잠금·파일 검증, 감사 기록, 실패 입력 보존 |
| 심사 라운드·기준·대상 준비 | `운영 화면` | 0..N 라운드, 기준, 대상 entry, 오픈·마감 연장·최종 확정 구현 |
| 심사위원·배정·외부 심사 링크 | `구현·회귀 완료` | 이름·역할 수정 API 연결, 배정 이력 시 차단·링크 철회·감사 기록. 구형 심사 URL은 대회·fragment token을 보존하며 query token을 fragment로 이동 |
| 점수 제출·집계·순위 | `운영 화면` | 링크 토큰 기반 평가표 조회·제출, `TOP_N`·`MIN_SCORE`·`MANUAL` 결과 확정 구현 |
| 수상 후보·공동순위·확정 | `운영 화면` | 후보 계산·조정·보류·확정과 참가자 결과 조회가 연결됨 |
| 주요 상태 변경 감사 | `내부 운영` | 초대·승인·대회·팀·심사·증빙 등 주요 작업을 감사 로그로 남김 |

### 3. Credential·Merkle·Kaia/Sui

| 기능 | 상태 | 현재 근거와 경계 |
| --- | --- | --- |
| 활동 Credential 발급 | `운영 화면` | 현재 발급 타입은 `PARTICIPATION`, `WORK`, `AWARD` 3종. 팀·제출·수상 확정 경로에 연결됨 |
| canonical JSON·해시 | `내부 운영` | Unicode NFC, RFC 8785 JCS, SHA-256과 Keccak-256을 사용 |
| Merkle batch·proof | `내부 운영` | OpenZeppelin Standard Merkle Tree 호환 leaf·root·proof 생성과 Java/Solidity fixture가 존재 |
| EIP-712 기관 승인 | `제한 운영` | batch와 폐기·대체 승인, nonce·digest 재사용 방지가 구현됐으나 운영 키 거버넌스는 미완료 |
| Kaia 앵커링 | `제한 운영` | 기존 운영 DB에서 Kairos AWARD ANCHORED 6개·batch 1개·CONFIRMED tx 1개·key 1개 확인. 기존 서비스를 유지하며 기본 설정은 `DISABLED`, worker 기본값은 `false` |
| Sui 승인·앵커링 | `제한 운영` | Move Registry·SDK gateway·Java adapter·기관 low-S ECDSA 전용 도메인 구현. 영속 MySQL→testnet 3종 발급·PARTICIPATION 취소 1/1 PASS·4거래 CONFIRMED, 재시작 후 공개 HTTP/업무 보존과 Chrome 공개 결과 확인. 운영 이관·전체 브라우저 coverage는 별도 gate |
| 기존 Kaia·Sui 읽기 라우팅 | `구현·회귀 완료` | 명시적 legacy Kairos allowlist로 조회만 공존. 쓰기는 active provider 한 개이며 legacy key version을 Sui로 재사용하거나 기존 Kaia 증명을 Sui에서 취소할 수 없음 |
| Transactional Outbox·재조정 | `내부 운영` | submit, receipt reconcile, lease·retry·unknown broadcast·read-back·dead 처리와 수동 reconcile API 구현 |
| 폐기·대체 상태 | `제한 운영` | `REVOKED`, `SUPERSEDED` event 생성·승인·갱신·reconcile API와 관리자 UI가 있으나 운영 릴리스·권한 절차 검증이 남음 |
| 공개 Credential 검증 | `구현·회귀 완료` | hash·Merkle·issuer·현재 효력 조회. 공개 subjectRef는 증명별 별칭이며 내부 사용자 PK를 노출하지 않음. 이름은 PUBLIC 지정에 따라 남으므로 현재 개인별 동의·익명성을 보장하지 않음 |
| 검증 패키지·PDF 인증서 | `운영 화면` | 공개 허용 요약과 proof ZIP, QR 검증 URL이 든 PDF를 제공. canonical 원문과 파일 원문은 제외. 후속 로컬 회귀에서 TAMPERED의 null 요약 처리와 모든 non-VALID PDF의 경고·유효 상장 문구 배제를 확인 |
| 학생 공개 활동 프로필 | `운영 화면` | UUID 공개 ID, 전체 on/off, 링크 rotate, 공개 Credential 요약 조회가 구현됨 |
| 활동·필드 단위 선택 공개 | `구현 전` | 현재는 프로필 전체 공개 여부와 개별 Credential 링크 선택만 가능 |
| 공개 subject 참조 최소화 | `구현·회귀 완료` | 개별 검증·패키지의 `subjectRef`는 `public-subject:<credentialPublicId>:<공개 순번>` 별칭으로 투영한다. 원본 canonical·hash·signature는 유지하며, 증명 간 공통 불변 식별자나 현재 개인별 동의 관리가 구현됐다는 뜻은 아님 |

> 블록체인은 현실 활동의 사실성을 만들지 않는다. 대학·기관이 사실을 확인하고, Trekkey는 승인 당시 내용의 무결성과 현재 효력을 검증한다. Merkle Proof는 영지식증명이 아니다.

### 4. 졸업요건 자가점검

| 기능 | 상태 | 현재 근거와 경계 |
| --- | --- | --- |
| 학적 프로필 | `제한 운영` | 입학연도·유형·졸업 경로·학사조직 등을 저장. 격리 HTTP 저장/조회와 재시작 후 동일 profile·3학점 보존 확인; 공식 학사시스템 동기화는 아님 |
| 성적표·활동 가져오기 | `제한 운영` | 학생 PDF/CSV preview·명시적 적용. MySQL/HTTP에서 성적 CSV import·course PATCH 후 재시작해 동일 과목 ID·MAJOR_REQUIRED·현재 기관 학사 단위 보존 확인. 개인 로그인 영역은 크롤링하지 않음 |
| 요건 평가 | `제한 운영` | `SATISFIED`·`UNSATISFIED`·`UNKNOWN`, 입력 완전성·정책 snapshot·면책문구 구현. 후속 로컬 회귀에서 coverage·학적 누락 시 전체 충족 오표시 차단과 입력 snapshot 보강을 확인. 이전 격리 HTTP 평가 4회는 공식 판정의 정책 정확성 입증과 다름 |
| Trekkey 활동 재사용 | `제한 운영` | 참여·수상 Credential을 비교과 L3 근거로 동기화하지만 졸업 판정 자체를 새 Credential로 발급하지 않음 |
| 한성대 공식 출처 확인 | `제한 운영` | 관리자 source sync가 공개 공식 URL의 변경을 확인. 네트워크 실패와 원천 변경은 수동 검토 대상 |
| 학과·트랙·연도별 완전한 정책 | `구현 전` | 현재 bootstrap은 일부 단위와 공통 수치 중심이다. 2015 이전·편입·전체 트랙·학적 상태와 단위 정책 누락을 완전하게 처리하지 못함 |
| 공식 졸업 판정 | `구현 전` | 종합정보시스템·학사 승인과 연결되지 않았으며 학교의 공식 졸업사정을 대체하지 않음 |
| 졸업·교과목·자격증 Credential | `구현 전` | 현재 Credential schema profile은 공모전 활동 3종만 지원 |

### 5. 외부 증빙

| 기능 | 상태 | 현재 근거와 경계 |
| --- | --- | --- |
| 학생 증빙 bundle 제출 | `제한 운영` | PDF/JPEG/PNG 최대 5개, 파일당 10MB·합계 25MB, 형식·크기·decode·SHA-256 검사. 프론트 비동기 후 form reset 오류 수정, 격리 HTTP PDF 제출 확인 |
| 자격번호 최소 저장 | `내부 운영` | 원문 대신 HMAC과 마지막 네 자리 저장. 격리 runtime은 JWT와 별도 HMAC secret 주입 완료; 코드의 JWT fallback 제거/운영 필수화는 남음 |
| 관리자 2인 수동 검수 | `제한 운영` | 서로 다른 관리자 승인·반려 합의와 조직/본인 경계 구현. 격리 HTTP에서 PDF 2인 승인·동일 관리자 재검수 409 확인, 재시작 후 VERIFIED·2 reviews·L2·파일 hash 보존 확인 |
| 졸업 비교과 연결 | `제한 운영` | 최종 승인 시 `DOCUMENT_VERIFIED`, L2 비교과 기록과 binding을 생성 |
| 발급기관 직접 검증 | `구현 전` | 기관 API, 전자서명, 이메일 challenge, provider webhook과 발급기관 registry 미구현 |
| 파일 보안·자동 추출 | `구현 전` | 현재 `FORMAT_VALIDATED`는 악성코드 검사를 뜻하지 않는다. malware scan·OCR·object storage 미구현 |
| 만료·철회 자동 재평가 | `구현 전` | 평가 시 만료 자료를 제외하지만 decision 만료 전환, revoke·binding 해제·재평가 worker는 없음 |

### 6. 검증·발표·연구 화면

| 기능 | 상태 | 현재 근거와 경계 |
| --- | --- | --- |
| Tamper Lab | `구현·회귀 완료` | Sui/Kaia 명시 좌표·체크포인트와 실제 leaf/proof 재계산 연결. RPC 실패·증거 누락·불일치 시 성공 차단. 과거 고정 테스트 수 제거, Kaia gas 표본을 Sui 비용으로 표시하지 않음 |
| 브라우저 성능 리포트 | `연구 화면` | 1·10·100·500·1,000 leaf의 브라우저 암호 계산 측정. 네트워크·DB·체인 확정 TPS가 아님 |
| 10분 발표 웹·Demo Theater | `내부 운영` | 발표용 결정적 replay와 실제 브라우저 crypto를 조합. 운영 POST를 수행하지 않음 |
| 공개 홈·게스트 대회 상세 | `제한 운영` | 게스트는 운영 DB가 아니라 로컬 fixture를 사용하므로 실서비스 공개 목록으로 표현하면 안 됨 |

### 7. 배포·운영

| 기능 | 상태 | 현재 근거와 경계 |
| --- | --- | --- |
| 프론트 Vercel 빌드·배포 | `내부 운영` | Vite build와 Vercel rewrite 구성 존재. 인증 API는 first-party cookie를 위해 동일 출처 rewrite 사용 |
| 백엔드 컨테이너 배포 | `제한 운영` | 이전 v6 18080 적용·재시작 후 독립 HTTP·업무 readback·Chrome 공개 화면 확인. 격리30/full48 tables 분리 보존, 기존 운영 container ID 변경 없이 HTTP 200·Kaia 수상 6개 유지. 후속 새 JAR은 원격 미반영이며 실제 legacy 검증·기관 키·기존 DB upgrade 게이트는 남음 |
| HTTPS 설정 | `제한 운영` | 별도 수동 setup script이며 CD 파이프라인의 자동 게이트가 아님 |
| DB migration 운영 | `제한 운영` | 수동 SQL 존재, 격리 full runtime은 빈 schema bootstrap 후 validate 사용. 자동 migration runner와 기존 데이터 upgrade/rollback은 별도 gate |
| 백업·DB 복원 | `제한 운영` | EC2 root 0700 온라인 snapshot을 별도 network=none MySQL에 실제 복원: 48 tables·AWARD 6·batch 1·tx 1·key 1 확인. 복원 container 정지·volume 보존; write-paused 일관성/전체 앱 복구·RPO/RTO는 미검증 |
| 관측·alert·readiness | `구현 전` | Actuator, metrics, alert, blockchain readiness의 완성된 운영 체계는 아직 없음 |

## 현재 가장 중요한 제품 경계

1. 공모전 운영에서 확정된 참여·작품·수상은 실제 Credential로 발급된다.
2. 공개 검증은 발급 이후 변경 여부와 현재 효력을 확인하지만 현실 사실 자체를 대신 판단하지 않는다.
3. 졸업요건은 일부 한성대 규칙을 다루는 비공식 자가점검 MVP다.
4. 외부 증빙은 기관 직접 검증이 아닌 L2 2인 수동 검수 MVP다.
5. 공개 subjectRef의 내부 숫자 ID는 증명별 별칭으로 대체했다. PUBLIC 이름·전공과 발급 당시 공개 요약은 남으며 현재 개인별 동의·철회나 완전한 익명성을 보장하지 않는다.
6. 혼합 체인 읽기와 Tamper Lab의 Sui UI 계약은 로컬 코드·회귀 완료다. 새 JAR 원격 반영, 실제 운영 legacy 검증·기관 키·기존 DB upgrade·legacy 쓰기 및 전체 브라우저 검증은 별도이므로 운영 완전 대체를 주장하지 않는다.
