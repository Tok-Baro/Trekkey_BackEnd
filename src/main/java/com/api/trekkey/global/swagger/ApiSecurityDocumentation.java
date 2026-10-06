package com.api.trekkey.global.swagger;

import com.api.trekkey.global.security.jwt.JwtProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;

/** API별 인증 조건을 Swagger에 표시한다. 실제 접근 제어는 보안 설정과 컨트롤러에서 처리한다. */
final class ApiSecurityDocumentation {
    static final String REFRESH_COOKIE_SCHEME = "Refresh Cookie";
    private final JwtProperties jwt;

    ApiSecurityDocumentation(JwtProperties jwt) { this.jwt = jwt; }

    void customise(OpenAPI api) {
        api.getComponents().addSecuritySchemes(REFRESH_COOKIE_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name(jwt.getRefreshCookieName())
                .description("로그인·인증 갱신 시 발급하는 HttpOnly Refresh Token 쿠키입니다. Path=/api/auth. "
                        + "Authorization 헤더나 요청 본문이 아닌 쿠키로 전달합니다. Secure=" + jwt.isRefreshCookieSecure()
                        + ", SameSite=" + jwt.getRefreshCookieSameSite() + ". 브라우저 요청에는 credentials 설정이 필요합니다."));
        if (api.getPaths() == null) return;
        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            operation.setSecurity(List.of(new SecurityRequirement().addList(SwaggerConfig.JWT_SCHEME_NAME)));
            if (publicWithoutJwt(path, method)) operation.setSecurity(List.of());
            if (reviewCapability(path, method)) {
                operation.setSecurity(List.of());
                describe(operation, "Access Token 대신 유효한 심사 링크 토큰이 필요합니다. "
                        + "API 키 헤더가 아닌 요청 본문의 `token` 필드로 전달합니다. "
                        + "JSON 요청은 application/json을 사용하며, 파일 다운로드 폼은 "
                        + "application/x-www-form-urlencoded도 지원합니다. 토큰을 URL이나 로그에 노출하지 마세요.");
                if (operation.getRequestBody() != null) operation.getRequestBody().setRequired(true);
            }
            if (method == HttpMethod.POST && path.equals("/api/auth/refresh")) {
                operation.setSecurity(List.of(new SecurityRequirement().addList(REFRESH_COOKIE_SCHEME)));
                describe(operation, "Access Token이나 요청 본문 대신 HttpOnly `" + jwt.getRefreshCookieName()
                        + "` 쿠키로 인증합니다. 갱신에 성공하면 Set-Cookie로 Refresh Token 쿠키를 교체합니다. "
                        + "Refresh Token이 누락되었거나 만료·폐기·재사용된 경우 요청을 거절합니다.");
            }
            if (method == HttpMethod.POST && path.equals("/api/auth/logout")) {
                operation.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList(REFRESH_COOKIE_SCHEME)));
                describe(operation, "Access Token은 필요하지 않으며, HttpOnly `" + jwt.getRefreshCookieName()
                        + "` 쿠키는 선택 사항입니다. 쿠키가 있으면 해당 세션의 Refresh Token 묶음(family)을 폐기합니다. "
                        + "쿠키가 없어도 CSRF 검증을 통과하면 성공합니다. "
                        + "응답의 Set-Cookie로 쿠키를 삭제합니다(Path=/api/auth, Max-Age=0).");
            }
            if (method == HttpMethod.POST && path.equals("/api/auth/signin")) {
                describe(operation, "기존 Access Token 없이 JSON 본문에 로그인 정보를 전달합니다. "
                        + "응답으로 Access Token과 /api/auth 경로의 HttpOnly `" + jwt.getRefreshCookieName()
                        + "` 쿠키를 발급합니다. 브라우저에서 인증 갱신·로그아웃 시 쿠키를 전송하도록 credentials를 설정하세요.");
            }
            if (method == HttpMethod.GET && path.equals("/api/auth/csrf")) {
                describe(operation, "인증 관련 상태 변경 요청 전에 credentials를 설정하여 호출합니다. "
                        + "HttpOnly XSRF-TOKEN 쿠키를 발급하고 data.headerName과 data.token을 반환합니다. "
                        + "쿠키 원본 값이 아닌 응답의 마스킹된 토큰을 요청 헤더로 전달하세요. "
                        + "이 응답은 캐시하지 않습니다.");
            }
            if (path.startsWith("/api/auth/")
                    && !List.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE).contains(method)) {
                operation.addParametersItem(new HeaderParameter()
                        .name("X-XSRF-TOKEN").required(true).schema(new StringSchema())
                        .description("GET /api/auth/csrf 응답의 data.token입니다. 발급 시 받은 XSRF-TOKEN 쿠키도 함께 전송해야 합니다."));
                describe(operation, "CSRF 검증이 필요합니다. 먼저 GET /api/auth/csrf를 호출한 뒤, "
                        + "발급받은 XSRF-TOKEN 쿠키와 data.token 값의 X-XSRF-TOKEN 헤더를 함께 전송하세요. "
                        + "토큰이 누락되거나 일치하지 않으면 403을 반환합니다. "
                        + "HttpOnly 쿠키를 JavaScript로 읽지 말고, credentials 설정으로 전송하세요.");
            }
        }));
        requireBodyToken(api, "ReviewAccessReq");
        requireBodyToken(api, "ReviewSubmitReq");
    }

    private static boolean publicWithoutJwt(String path, HttpMethod method) {
        return path.equals("/api/auth") || path.startsWith("/api/auth/")
                || path.equals("/api/organizations") || path.startsWith("/api/organizations/")
                || (method == HttpMethod.GET && (path.equals("/api/public/credentials")
                    || path.startsWith("/api/public/credentials/") || path.equals("/api/public/activity-profiles")
                    || path.startsWith("/api/public/activity-profiles/")));
    }

    private static boolean reviewCapability(String path, HttpMethod method) {
        return method == HttpMethod.POST && (path.equals("/api/review/access") || path.equals("/api/review/assignments")
                    || path.matches("/api/review/files/[^/]+/download(?:/check)?"))
                || method == HttpMethod.PUT && path.matches("/api/review/assignments/[^/]+/review");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void requireBodyToken(OpenAPI api, String schemaName) {
        if (api.getComponents().getSchemas() == null) return;
        Schema schema = api.getComponents().getSchemas().get(schemaName);
        if (schema == null || schema.getProperties() == null) return;
        Schema token = (Schema) schema.getProperties().get("token");
        if (token == null) return;
        token.setDescription("심사 링크 접근 권한을 확인하는 필수 토큰입니다. JWT나 API 키 헤더가 아닌 요청 본문으로 전달합니다.");
        token.setWriteOnly(true);
        List<String> required = schema.getRequired() == null ? new ArrayList<>() : new ArrayList<>(schema.getRequired());
        if (!required.contains("token")) required.add("token");
        schema.setRequired(required);
    }

    private static void describe(Operation operation, String additional) {
        String existing = operation.getDescription();
        if (existing == null || existing.isBlank()) operation.setDescription(additional);
        else if (!existing.contains(additional)) operation.setDescription(existing + "\n\n" + additional);
    }
}
