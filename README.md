# Trekkey API

대학생 공모전 탐색과 참여를 지원하는 **Trekkey** 서비스의 Spring Boot REST API입니다.

현재 `main` 브랜치는 사용자 인증과 학교(기관) 검색을 중심으로 구성되어 있습니다. 공통 응답 형식, 전역 예외 처리, JWT 인증 필터, Swagger 문서화 기반도 함께 제공합니다.

## 주요 기능

- 일반 사용자 회원가입
- 이메일·비밀번호 로그인
- JWT access token 발급
- HttpOnly 쿠키 기반 refresh token rotation
- refresh token 재사용 감지 및 토큰 패밀리 폐기
- 로그아웃 및 refresh token 폐기
- 활성 학교 목록 검색
- 공통 성공·오류 응답과 전역 예외 처리
- Swagger/OpenAPI 문서

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.14 |
| Security | Spring Security, JWT (`jjwt` 0.13.0) |
| Data | Spring Data JPA, QueryDSL, MySQL |
| Validation | Jakarta Bean Validation |
| API Docs | springdoc-openapi / Swagger UI |
| Build & Test | Gradle, JUnit 5 |

## 프로젝트 구조

```text
src/main/java/com/api/trekkey
├── domain
│   ├── auth          # 로그인, 토큰 발급·재발급·로그아웃
│   ├── organization  # 학교 검색
│   └── user          # 사용자 엔티티와 회원가입 DTO
└── global
    ├── config        # Security, QueryDSL, Web 설정
    ├── exception     # 공통 예외 처리
    ├── response      # 공통 API 응답
    ├── security      # JWT 인증 필터와 핸들러
    └── swagger       # OpenAPI 설정
```

## API 요약

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/signup` | 사용자 회원가입 | 불필요 |
| `POST` | `/api/auth/signin` | 로그인 및 토큰 발급 | 불필요 |
| `POST` | `/api/auth/refresh` | access/refresh token 재발급 | refresh cookie |
| `POST` | `/api/auth/logout` | 로그아웃 및 refresh token 폐기 | refresh cookie |
| `GET` | `/api/organizations?keyword=` | 활성 학교 검색 | 불필요 |

상세 요청·응답 스키마는 애플리케이션 실행 후 Swagger UI에서 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 실행 환경

다음 도구가 필요합니다.

- JDK 21
- MySQL

환경별 설정은 `src/main/resources/application.properties` 또는 환경변수로 주입합니다.

| 환경변수 | 설명 | 기본값 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | 없음 |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자명 | 없음 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | 없음 |
| `JWT_SECRET` | JWT 서명 키 | 개발용 기본값 |
| `JWT_ACCESS_EXPIRATION` | access token 유효기간(초) | `1800` |
| `JWT_REFRESH_EXPIRATION` | refresh token 유효기간(초) | `1209600` |
| `JWT_REFRESH_COOKIE_NAME` | refresh cookie 이름 | `refreshToken` |
| `JWT_REFRESH_COOKIE_SECURE` | HTTPS 전용 쿠키 여부 | `false` |
| `JWT_REFRESH_COOKIE_SAME_SITE` | SameSite 정책 | `Lax` |

> 운영 환경에서는 충분히 긴 `JWT_SECRET`을 반드시 별도로 주입하고, HTTPS 환경에서 `JWT_REFRESH_COOKIE_SECURE=true`를 사용하세요.

## 로컬 실행

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/trekkey'
export SPRING_DATASOURCE_USERNAME='root'
export SPRING_DATASOURCE_PASSWORD='your-password'
export JWT_SECRET='your-production-grade-secret-key'

./gradlew bootRun
```

애플리케이션은 기본적으로 `http://localhost:8080`에서 실행됩니다.

## 테스트

```bash
./gradlew test
```

MySQL 통합 테스트를 포함하고 있으므로 테스트 환경의 데이터베이스 설정이 필요할 수 있습니다.

## 관련 문서

- [ERD](docs/erd.md)
