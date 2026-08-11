# Trekkey 설계 문서

팀 구현 기준 문서는 다음 일곱 개다.

1. [업무·블록체인 전체 통합 ERD](./unified-erd.md)
   - 업무 SQL 16개, 인증·관리 SQL 3개, 앵커링 SQL 9개를 한 캔버스에 표시
   - 신청·제출 일정은 `CONTEST`, 가변 심사는 `REVIEW_ROUND`로 분리
   - [확대용 SVG](./assets/trekkey-unified-erd.svg)
   - [Raw Mermaid](./trekkey-unified-erd.mmd)

2. [팀 회의용 Mermaid 다이어그램 보드](./architecture-diagrams.md)
   - 전체 시스템과 신뢰 경계
   - 업무·Credential 축약 ERD
   - 대회, 심사, 발급, 앵커링, 검증 흐름
   - 상태 머신과 장애 복구
   - 구현 의존 순서

3. [공모전·Credential 최종 ERD](./erd.md)
   - 업무 SQL ERD
   - Credential·앵커링 SQL ERD
   - 필수 UNIQUE, CHECK, 잠금 규칙
   - JPA 및 후속 확장 기준

4. [Credential 및 Kaia 앵커링 설계](./blockchain-anchoring-architecture.md)
   - 신뢰 경계와 저장 위치
   - canonical JSON, source fingerprint, file manifest
   - Merkle leaf·proof 규칙
   - EIP-712 학교 승인과 표준 EVM relayer
   - 검증, 폐기, 대체, 장애 복구, 테스트 기준

5. [블록체인 구현 및 Kairos 실행 가이드](./blockchain-implementation-runbook.md)
   - 현재 구현 완료·미연결 범위
   - 역할과 키 분리
   - Kairos 배포 및 issuer 등록
   - Spring 환경변수와 관리자 API 실행 순서
   - 팀 업무 서비스가 연결할 `CredentialIssuanceService`
   - 운영 전 필수 체크리스트

6. [2026-07-27 통합 회의 안건](./meetings/2026-07-27-integration-agenda.md)
   - 보안 조치와 커밋 추적
   - 팀원·제출·심사·수상 인수 기준
   - Credential·Kairos 운영 결정
   - 담당자·회의 순서·병합 체크리스트

7. [SW·AI중심대학 졸업요건 검사 설계](./graduation-requirement-design.md)
   - 2025년 SW중심대학 58개교 대상 레지스트리
   - 학교·학번·전공·입학유형별 버전형 졸업정책
   - 성적표·비교과 증빙과 `SATISFIED/UNSATISFIED/UNKNOWN` 판정
   - 한성대학교 공통 졸업요건 파일럿 규칙

8. 한성대학교 졸업요건 MVP 문서 세트
   - [기능 설계서](./hansung-graduation-requirement-design.md)
   - [ERD 및 테이블 명세](./hansung-graduation-erd.md)
   - [API 및 프론트 계약](./hansung-graduation-api-spec.md)
   - [3-cycle QA 보고서](./hansung-graduation-qa-report.md)
   - 한성대 공식 학사기준과 현재 백엔드·프론트 구조를 연결한 구현 기준

## 현재 구현 범위

MVP 설계는 공모전 참여, 최종 제출 작품, 0..N개의 Review Round, 팀 수상 Credential을 대상으로 한다. 현재 코드는 대회·팀·제출·심사·수상과 Credential·Merkle·Solidity·Kaia adapter가 함께 있고, 수상 확정에서 Credential 발급까지 연결돼 있다. 팀원 등록·학번 검색 API도 포함됐다. 리뷰 실행 원장은 `REVIEW_ROUND`/`REVIEW_ROUND_ENTRY`로 전환됐고, 신청·제출 같은 비리뷰 일정만 `CONTEST_STAGE`에 남아 있다. `origin/develop`의 구형 리뷰 stage 구조는 기존 DB 마이그레이션 기준이다. 졸업요건, 학적 이력, 공모전 외 독립 작품, 제출 버전은 실제 업무 요구가 확정될 때 확장한다.

문서와 구현이 충돌하면 임의로 해석하지 말고 ERD 결정사항을 먼저 갱신한다. 특히 hash 입력, schema profile, Merkle tree version은 배포 후 조용히 변경하면 안 된다.
