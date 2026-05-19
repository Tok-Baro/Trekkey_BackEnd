# Spring Boot Template Guide

이 템플릿은 Spring Boot REST API 프로젝트를 시작할 때 반복되는 기본 구성을 미리 담아둔 프로젝트입니다.

## 기본 포함 구성

- 공통 성공/실패 응답 포맷
- 전역 예외 처리
- JPA `BaseEntity`와 auditing
- QueryDSL 설정
- Swagger UI 설정
- Swagger JWT bearer 인증 설정
- Swagger 에러 응답 예시 자동화
- JWT 생성/검증/인증 필터 기본 구조
- CORS 기본 설정

## 새 프로젝트 시작 시 변경할 것

1. 패키지명 `com.api.template`을 새 프로젝트 패키지명으로 변경합니다.
2. `settings.gradle`의 `rootProject.name`을 새 프로젝트명으로 변경합니다.
3. `build.gradle`의 `description`을 새 프로젝트명으로 변경합니다.
4. `application.properties`의 DB URL, username, password를 실제 MySQL 환경에 맞게 변경합니다.
5. `app.cors.allowed-origin`을 프론트엔드 주소에 맞게 변경합니다.
6. Swagger 문서 제목과 설명을 `SwaggerConfig`에서 프로젝트에 맞게 변경합니다.
7. `security.jwt.secret-key`는 운영 환경에서 반드시 새 값으로 교체합니다.

## 응답 포맷 사용법

성공 응답은 `SuccessResponse`로 감쌉니다.

```java
@GetMapping("/users/{userId}")
public ResponseEntity<SuccessResponse<UserRes>> getUser(@PathVariable Long userId) {
    UserRes response = userService.getUser(userId);
    return ResponseEntity.ok(SuccessResponse.ok(response));
}
```

생성 응답은 `201 Created`와 함께 사용할 수 있습니다.

```java
@PostMapping("/users")
public ResponseEntity<SuccessResponse<UserRes>> createUser(@RequestBody @Valid UserCreateReq request) {
    UserRes response = userService.createUser(request);
    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(SuccessResponse.create(response));
}
```

## 에러 코드 작성법

도메인별 에러 enum은 `BaseResponseCode`를 구현합니다.

```java
@Getter
@RequiredArgsConstructor
public enum UserErrorResponseCode implements BaseResponseCode {
    USER_NOT_FOUND_404("USER_NOT_FOUND_404", 404, "사용자를 찾을 수 없습니다."),
    DUPLICATED_EMAIL_409("DUPLICATED_EMAIL_409", 409, "이미 사용 중인 이메일입니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
```

서비스에서는 `CustomException`으로 던집니다.

```java
throw new CustomException(UserErrorResponseCode.USER_NOT_FOUND_404);
```

## Validation 에러 응답

`@Valid` 검증 실패 시 어떤 필드가 실패했는지 `data`에 배열로 내려갑니다.

```json
{
  "isSuccess": false,
  "timestamp": "2026-05-18 17:00:00",
  "code": "GLOBAL_400_BODY",
  "httpStatus": 400,
  "message": "요청 값 검증에 실패했습니다.",
  "data": [
    {
      "field": "email",
      "rejectedValue": "wrong-email",
      "message": "이메일 형식이 아닙니다."
    },
    {
      "field": "password",
      "rejectedValue": "123",
      "message": "비밀번호는 8자 이상이어야 합니다."
    }
  ]
}
```

요청 바디 검증 실패는 `GLOBAL_400_BODY`, 쿼리 파라미터나 모델 바인딩 검증 실패는 `GLOBAL_400_PARAMETER`로 응답합니다.

## QueryDSL 사용법

이 템플릿은 원본 `com.querydsl` 대신 유지보수 fork인 OpenFeign QueryDSL을 사용합니다. 코드에서 사용하는 패키지는 기존과 동일하게 `com.querydsl.*`입니다.

```gradle
implementation 'io.github.openfeign.querydsl:querydsl-jpa:5.6.1:jakarta'
annotationProcessor 'io.github.openfeign.querydsl:querydsl-apt:5.6.1:jakarta'
```

엔티티를 추가한 뒤 `./gradlew compileJava`를 실행하면 `build/generated/querydsl`에 Q클래스가 생성됩니다. 도메인별 조회 요구사항은 별도 QueryRepository를 만들어 작성합니다.

```java
@Repository
@RequiredArgsConstructor
public class UserQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<User> findActiveUsers(String keyword) {
        return queryFactory
                .selectFrom(user)
                .where(
                        user.deleted.isFalse(),
                        keyword != null ? user.name.containsIgnoreCase(keyword) : null
                )
                .orderBy(user.createdAt.desc())
                .fetch();
    }
}
```

요청 파라미터로 받은 정렬 필드명을 `PathBuilder.get(sort)`처럼 직접 넣지 않습니다. 정렬 값은 enum이나 whitelist로 검증한 뒤 `QEntity.field`에 매핑합니다.

## JWT 사용법

JWT 템플릿은 특정 `User` 엔티티나 `UserRepository`에 의존하지 않습니다. 로그인 로직에서 유저를 직접 조회하고, 검증이 끝난 뒤 `AuthPrincipal`을 만들어 토큰을 발급합니다.

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public TokenDto login(LoginReq request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.LOGIN_FAILED_401));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(UserErrorResponseCode.LOGIN_FAILED_401);
        }

        AuthPrincipal principal = AuthPrincipal.of(
                user.getId(),
                user.getEmail(),
                List.of(user.getRole().name())
        );

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );

        return jwtTokenProvider.createTokens(authentication);
    }
}
```

`AuthPrincipal.of()`에 넘기는 role은 `USER`, `ADMIN`처럼 넘겨도 내부에서 `ROLE_USER`, `ROLE_ADMIN` authority로 변환됩니다. 이미 `ROLE_` prefix가 붙은 값도 그대로 사용할 수 있습니다.

### JWT 코드 유지/확장 기준

사용자 도메인을 추가해도 보통 아래 파일들은 그대로 유지합니다.

```text
global/security/AuthPrincipal.java
global/security/jwt/JwtTokenProvider.java
global/security/jwt/JwtExtractor.java
global/security/jwt/JwtAuthenticationFilter.java
global/security/jwt/JwtProperties.java
global/security/jwt/TokenDto.java
global/security/handler/JwtAuthenticationEntryPoint.java
global/security/handler/JwtAccessDeniedHandler.java
```

사용자 도메인을 만들 때 프로젝트에서 추가로 작성할 부분은 아래입니다.

```text
domain/user/entity/User.java
domain/user/entity/Role.java
domain/user/repository/UserRepository.java
domain/auth/service/AuthService.java
domain/auth/web/controller/AuthController.java
domain/auth/web/dto/LoginReq.java
```

프로젝트마다 수정할 가능성이 높은 부분은 `SecurityConfig`의 URL 권한 정책입니다.

```java
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/users/me/**").hasRole("USER")
.anyRequest().authenticated()
```

`AuthPrincipal`은 기본적으로 `id`, `username`, `authorities`만 가집니다. 거의 모든 요청에서 필요한 값이 있다면 나중에 필드를 추가할 수 있습니다.

```java
private final Long companyId;
private final Long tenantId;
```

이 경우 JWT 생성 시 claim을 추가하고, 파싱 시 다시 꺼내도록 `JwtTokenProvider`도 함께 수정해야 합니다.

```java
.claim("companyId", principal.getCompanyId())
```

```java
Long companyId = claims.get("companyId", Long.class);
```

단, 회사/조직 소속처럼 변경될 수 있는 값은 기존 토큰에 오래 남을 수 있습니다. 중요한 권한 검증은 서비스에서 DB 기준으로 다시 확인합니다.

인증된 API에서는 `@AuthenticationPrincipal`로 현재 사용자 정보를 꺼냅니다.

```java
@GetMapping("/users/me")
public ResponseEntity<SuccessResponse<UserMeRes>> getMe(
        @AuthenticationPrincipal AuthPrincipal principal
) {
    UserMeRes response = userService.getMe(principal.getId());
    return ResponseEntity.ok(SuccessResponse.ok(response));
}
```

서비스에서는 보통 `AuthPrincipal` 전체가 아니라 `principal.getId()`로 얻은 userId만 넘기고, 필요한 엔티티와 비즈니스 권한은 DB 기준으로 다시 검증합니다.

```java
@Transactional(readOnly = true)
public UserMeRes getMe(Long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND_404));

    return UserMeRes.from(user);
}
```

기본 `SecurityConfig`는 Swagger와 `/api/auth/**`를 `permitAll`로 열고, 나머지 요청은 인증을 요구합니다.

```java
.requestMatchers("/api/auth/**").permitAll()
.anyRequest().authenticated()
```

프로젝트별 권한 규칙은 `SecurityConfig`에서 추가합니다.

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/users/me/**").hasRole("USER")
```

### Security 인증/인가 실패 응답

Spring Security 필터 단계에서 발생하는 인증/인가 실패는 `GlobalExceptionHandler`가 아니라 Security handler에서 처리합니다.

토큰이 없거나 유효하지 않은 토큰으로 인증이 실패하면 `JwtAuthenticationEntryPoint`가 `401 Unauthorized`를 반환합니다.

```json
{
  "isSuccess": false,
  "timestamp": "2026-05-19 14:00:00",
  "code": "GLOBAL_401",
  "httpStatus": 401,
  "message": "인증이 필요합니다.",
  "data": null
}
```

인증은 되었지만 필요한 권한이 없으면 `JwtAccessDeniedHandler`가 `403 Forbidden`을 반환합니다.

```json
{
  "isSuccess": false,
  "timestamp": "2026-05-19 14:00:00",
  "code": "GLOBAL_403",
  "httpStatus": 403,
  "message": "해당 요청에 대한 접근 권한이 없습니다.",
  "data": null
}
```

## Swagger 에러 예시 사용법

컨트롤러 메서드에 `@ApiErrorCodeExamples`를 붙이면 Swagger 응답 예시가 자동으로 추가됩니다.

```java
@ApiErrorCodeExamples(
        value = UserErrorResponseCode.class,
        codes = {"USER_NOT_FOUND_404"}
)
@GetMapping("/users/{userId}")
public ResponseEntity<SuccessResponse<UserRes>> getUser(@PathVariable Long userId) {
    UserRes response = userService.getUser(userId);
    return ResponseEntity.ok(SuccessResponse.ok(response));
}
```

`codes`에는 enum 상수명 또는 실제 응답 code 값을 사용할 수 있습니다.

```java
@ApiErrorCodeExamples(
        value = UserErrorResponseCode.class,
        codes = {"USER_NOT_FOUND_404", "DUPLICATED_EMAIL_409"}
)
```

`codes`를 생략하면 해당 enum의 모든 에러 코드가 Swagger 예시에 추가됩니다.

```java
@ApiErrorCodeExamples(UserErrorResponseCode.class)
```

여러 도메인 에러 enum을 한 API에 함께 붙일 수도 있습니다.

```java
@ApiErrorCodeExamples(
        value = UserErrorResponseCode.class,
        codes = {"USER_NOT_FOUND_404"}
)
@ApiErrorCodeExamples(
        value = DepartmentErrorResponseCode.class,
        codes = {"DEPARTMENT_NOT_FOUND_404"}
)
@PostMapping("/departments/{departmentId}/users/{userId}")
public ResponseEntity<SuccessResponse<Void>> addUserToDepartment(
        @PathVariable Long departmentId,
        @PathVariable Long userId) {
    departmentService.addUser(departmentId, userId);
    return ResponseEntity.ok(SuccessResponse.empty());
}
```

Swagger에 표시되는 에러 예시는 `ErrorResponse.from(errorCode)` 형태로 생성됩니다.

```json
{
  "isSuccess": false,
  "timestamp": "2026-05-18 17:00:00",
  "code": "USER_NOT_FOUND_404",
  "httpStatus": 404,
  "message": "사용자를 찾을 수 없습니다.",
  "data": null
}
```

## Swagger JWT 사용법

Swagger UI의 `Authorize` 버튼에서 JWT access token을 입력하면 됩니다.

```text
Bearer 토큰 전체를 넣지 말고 access token 문자열만 입력합니다.
```

Swagger 설정은 자동으로 요청 헤더에 다음 형식으로 인증 값을 붙입니다.

```http
Authorization: Bearer <access-token>
```

## 추천 도메인 패키지 구조

```text
domain/{feature}/
├── entity
├── repository
├── service
├── exception
└── web
    ├── controller
    └── dto
        ├── request
        └── response
```

## 실행 명령어

```bash
./gradlew compileJava
./gradlew test
./gradlew bootRun
```

`application.properties`의 DB URL이 placeholder라면 `./gradlew test` 또는 `bootRun` 전에 실제 `jdbc:` URL로 변경해야 합니다.

MySQL URL 예시는 아래와 같습니다.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/your_database?serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
spring.datasource.username=your_username
spring.datasource.password=your_password
```
