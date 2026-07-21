# Trekkey API

Trekkey 백엔드 Spring Boot REST API입니다.

## Included

- Standard success/error response wrapper
- Global exception handler
- JPA `BaseEntity` with auditing
- OpenFeign QueryDSL configuration
- Swagger UI configuration
- JWT security infrastructure
- Swagger error response examples
- Basic CORS configuration

## Commands

```bash
./gradlew test
./gradlew bootRun
```

Swagger UI is available at `/swagger-ui.html`.

## Design Docs

- [팀 회의용 Mermaid 다이어그램 보드](./docs/architecture-diagrams.md)
- [공모전·Credential 최종 ERD](./docs/erd.md)
- [Credential 및 Kaia 앵커링 설계](./docs/blockchain-anchoring-architecture.md)
- [설계 문서 인덱스](./docs/README.md)
