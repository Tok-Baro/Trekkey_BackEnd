package com.api.trekkey.global.swagger;

import com.api.trekkey.global.security.jwt.JwtProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;

/** Mirrors the existing HTTP security boundary; it does not install filters or change authorization. */
final class ApiSecurityDocumentation {
    static final String REFRESH_COOKIE_SCHEME = "Refresh Cookie";
    private final JwtProperties jwt;

    ApiSecurityDocumentation(JwtProperties jwt) { this.jwt = jwt; }

    void customise(OpenAPI api) {
        api.getComponents().addSecuritySchemes(REFRESH_COOKIE_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name(jwt.getRefreshCookieName())
                .description("HttpOnly refresh cookie set by signin/refresh; Path=/api/auth. "
                        + "Not an Authorization header or JSON body token. Secure=" + jwt.isRefreshCookieSecure()
                        + ", SameSite=" + jwt.getRefreshCookieSameSite() + ". Browser clients send it using credentials."));
        if (api.getPaths() == null) return;
        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            operation.setSecurity(List.of(new SecurityRequirement().addList(SwaggerConfig.JWT_SCHEME_NAME)));
            if (publicWithoutJwt(path, method)) operation.setSecurity(List.of());
            if (reviewCapability(path, method)) {
                operation.setSecurity(List.of());
                describe(operation, "Authentication: no access JWT. A valid secret review-link token is required. "
                        + "Supply it in the request body field `token`. JSON requests use application/json; the native "
                        + "file-download form also accepts application/x-www-form-urlencoded. This is not an API-key header. "
                        + "Do not put the secret in URLs or logs.");
                if (operation.getRequestBody() != null) operation.getRequestBody().setRequired(true);
            }
            if (method == HttpMethod.POST && path.equals("/api/auth/refresh")) {
                operation.setSecurity(List.of(new SecurityRequirement().addList(REFRESH_COOKIE_SCHEME)));
                describe(operation, "Authentication: requires the HttpOnly `" + jwt.getRefreshCookieName()
                        + "` cookie, not an access JWT or request body. A successful refresh rotates the cookie via Set-Cookie; "
                        + "missing, expired, revoked or reused refresh tokens are rejected.");
            }
            if (method == HttpMethod.POST && path.equals("/api/auth/logout")) {
                operation.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList(REFRESH_COOKIE_SCHEME)));
                describe(operation, "Authentication: no access JWT required. The HttpOnly `" + jwt.getRefreshCookieName()
                        + "` cookie is optional: if present its session family is revoked; without it logout still succeeds. "
                        + "The response clears the cookie using Set-Cookie (Path=/api/auth, Max-Age=0).");
            }
            if (method == HttpMethod.POST && path.equals("/api/auth/signin")) {
                describe(operation, "Authentication: credentials are supplied in the JSON body, not a pre-existing JWT. "
                        + "The response returns an access token and sets an HttpOnly `" + jwt.getRefreshCookieName()
                        + "` cookie for /api/auth; keep browser credentials enabled for refresh/logout.");
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
        token.setDescription("Required secret review-link capability token, supplied in the request body. Not a JWT or header API key.");
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
