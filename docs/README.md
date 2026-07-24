# Trekkey 설계 문서

팀 구현 기준 문서는 다음 네 개다.

1. [업무·블록체인 전체 통합 ERD](./unified-erd.md)
   - 업무 SQL 16개와 앵커링 SQL 9개를 한 캔버스에 표시
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

## 현재 구현 범위

MVP는 공모전 참여, 최종 제출 작품, 0..N개의 Review Round, 팀 수상 Credential을 대상으로 한다. 신청 기간과 제출 마감은 `CONTEST`가 직접 보유하며 별도 workflow stage는 만들지 않는다. 졸업요건, 학적 이력, 공모전 외 독립 작품, 제출 버전은 실제 업무 요구가 확정될 때 확장한다.

문서와 구현이 충돌하면 임의로 해석하지 말고 ERD 결정사항을 먼저 갱신한다. 특히 hash 입력, schema profile, Merkle tree version은 배포 후 조용히 변경하면 안 된다.
