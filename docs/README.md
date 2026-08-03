# Trekkey 설계 문서

팀 구현 기준 문서는 다음 아홉 개다.

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

6. [Kaia RPC와 EC2 배포 기준](./blockchain-rpc-and-ec2.md)
   - `BLOCKCHAIN_RPC_URL`의 의미와 실제 JSON-RPC 호출 흐름
   - public RPC, 관리형 RPC, 자체 Endpoint Node의 차이
   - 16GB 노드 시험 사양과 Trekkey 애플리케이션 EC2 사양 구분
   - 환경별 RPC 선택, 장애 판단과 배포 체크리스트

7. [2026-07-28 Kairos Registry 배포 기록과 재현 절차](./blockchain-kairos-deployment.md)
   - 실제 Registry 주소와 세 트랜잭션 증적
   - Kaia Wallet 계정 전환 및 승인 순서
   - issuer 소유 증명 typed data
   - calldata 디코딩과 최종 온체인 readback
   - 실패 복구와 백엔드 반영값

8. [Kairos 지속 사용 및 후속 개발 인계](./blockchain-kairos-continuation-plan.md)
   - Kairos를 계속 사용하는 현재 결정과 제한
   - 실제 학교 public ID 연결 분기
   - QR, AWS KMS, 모니터링·복구 우선순위와 완료 기준
   - Mainnet 전환 조건과 다음 개발자 시작 순서

9. [2026-07-27 통합 회의 안건](./meetings/2026-07-27-integration-agenda.md)
   - 보안 조치와 커밋 추적
   - 팀원·제출·심사·수상 인수 기준
   - Credential·Kairos 운영 결정
   - 담당자·회의 순서·병합 체크리스트

## 현재 구현 범위

MVP 설계는 공모전 참여, 최종 제출 작품, 0..N개의 Review Round, 팀 수상 Credential을 대상으로 한다. 현재 `develop`에는 대회·팀·제출·심사·수상과 Credential·Merkle·Solidity·Kaia adapter가 함께 있고, 수상 확정에서 Credential 발급까지 연결돼 있다. 팀원 등록·학번 검색 API도 포함됐다. 블록체인 Draft PR #16에서는 Kairos `chainId=1001` Registry, issuer key v2, backend relayer를 설정하고 실제 Credential 3건의 Merkle anchor, 공개 검증, revoke·supersede E2E까지 완료했다. 졸업작품·포트폴리오·학교 내부 베타는 현재 Kairos Registry를 계속 사용하며, 실제 장기 공식 증빙 요구가 확정되기 전에는 Mainnet 배포를 서두르지 않는다. 공용 MySQL public ID migration, QR 프론트 화면, AWS KMS adapter와 운영 복구 훈련은 남아 있다. 다만 실행 코드는 아직 `CONTEST_STAGE` 기반이며, 이 문서의 `REVIEW_ROUND` 목표 모델 전환은 혁모의 후속 PR 범위다. 졸업요건, 학적 이력, 공모전 외 독립 작품, 제출 버전은 실제 업무 요구가 확정될 때 확장한다.

문서와 구현이 충돌하면 임의로 해석하지 말고 ERD 결정사항을 먼저 갱신한다. 특히 hash 입력, schema profile, Merkle tree version은 배포 후 조용히 변경하면 안 된다.
