# Trekkey 설계 문서

팀 구현 기준 문서는 다음 두 개다.

1. [공모전·Credential 최종 ERD](./erd.md)
   - 업무 SQL ERD
   - Credential·앵커링 SQL ERD
   - 필수 UNIQUE, CHECK, 잠금 규칙
   - JPA 및 후속 확장 기준

2. [Credential 및 Kaia 앵커링 설계](./blockchain-anchoring-architecture.md)
   - 신뢰 경계와 저장 위치
   - canonical JSON, source fingerprint, file manifest
   - Merkle leaf·proof 규칙
   - EIP-712 학교 승인과 표준 EVM relayer
   - 검증, 폐기, 대체, 장애 복구, 테스트 기준

## 현재 구현 범위

MVP는 공모전 참여, 최종 제출 작품, 팀 수상 Credential을 대상으로 한다. 졸업요건, 학적 이력, 공모전 외 독립 작품, 제출 버전은 현재 ERD에 넣지 않고 실제 업무 요구가 확정될 때 확장한다.

문서와 구현이 충돌하면 임의로 해석하지 말고 ERD 결정사항을 먼저 갱신한다. 특히 hash 입력, schema profile, Merkle tree version은 배포 후 조용히 변경하면 안 된다.
