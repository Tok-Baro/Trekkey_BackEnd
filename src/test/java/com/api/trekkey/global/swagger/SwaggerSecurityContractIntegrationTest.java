package com.api.trekkey.global.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Fetches the actual generated /v3/api-docs, not a hand-assembled OpenAPI fixture. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:swagger-security-contract;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "security.jwt.secret-key=c2VjdXJpdHktY29uZmlnLXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtbG9uZy0xMjM0NTY3ODkwYWJjZGVm",
        "security.jwt.access-expiration=900", "security.jwt.refresh-expiration=604800",
        "security.jwt.refresh-cookie-name=docs-refresh", "security.jwt.refresh-cookie-secure=true",
        "security.jwt.refresh-cookie-same-site=None",
        "app.front.base-url=http://localhost:3000", "app.cors.allowed-origin=http://localhost:3000",
        "app.file.upload-dir=${java.io.tmpdir}/trekkey-swagger-test-uploads",
        "app.evidence.lookup-hmac-secret=synthetic-swagger-test-hmac-secret-over-thirty-two-bytes",
        "blockchain.anchoring.mode=DISABLED", "blockchain.anchoring.worker-enabled=false",
        "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
class SwaggerSecurityContractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    private JsonNode api;

    @BeforeEach void readGeneratedContract() throws Exception {
        api = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(api.path("paths").size()).isGreaterThan(50);
    }

    @Test void publicOperationsExplicitlyOverrideGlobalJwtAndProtectedOperationsKeepIt() {
        for (String path : List.of("/api/auth/signup", "/api/auth/signup/admin", "/api/auth/signin")) {
            assertNoJwt(path, "post");
        }
        assertNoJwt("/api/organizations", "get");
        assertNoJwt("/api/public/credentials/{credentialPublicId}", "get");
        assertNoJwt("/api/public/credentials/{credentialPublicId}/package", "get");
        assertNoJwt("/api/public/credentials/{credentialPublicId}/certificate", "get");
        assertNoJwt("/api/public/activity-profiles/{publicId}", "get");
        for (String path : List.of("/api/contests", "/api/me/graduation/profile", "/api/me/evidence-submissions")) {
            assertJwt(path, "get");
        }
        long protectedOperations = 0;
        var paths = api.path("paths").fields();
        while (paths.hasNext()) {
            var path = paths.next();
            if (!path.getKey().startsWith("/api/admin/") && !path.getKey().startsWith("/api/root/")) continue;
            var operations = path.getValue().fields();
            while (operations.hasNext()) {
                var operation = operations.next();
                if (!operation.getValue().has("responses")) continue;
                assertJwt(path.getKey(), operation.getKey()); protectedOperations++;
            }
        }
        assertThat(protectedOperations).isGreaterThan(30);
    }

    @Test void reviewCapabilityUsesRequiredBodyTokenInsteadOfInventingAHeaderSecurityScheme() {
        for (String path : List.of("/api/review/access", "/api/review/assignments",
                "/api/review/files/{fileId}/download", "/api/review/files/{fileId}/download/check")) {
            assertNoJwt(path, "post");
            JsonNode operation = operation(path, "post");
            assertThat(operation.path("description").asText()).contains("request body", "`token`", "not an API-key header");
            assertThat(operation.path("requestBody").path("required").asBoolean()).isTrue();
        }
        assertNoJwt("/api/review/assignments/{assignmentId}/review", "put");
        for (String schema : List.of("ReviewAccessReq", "ReviewSubmitReq")) {
            JsonNode body = api.path("components").path("schemas").path(schema);
            assertThat(body.path("required").toString()).contains("\"token\"");
            assertThat(body.path("properties").path("token").path("writeOnly").asBoolean()).isTrue();
        }
        JsonNode schemes = api.path("components").path("securitySchemes");
        assertThat(schemes.size()).isEqualTo(2);
        assertThat(schemes.toString()).doesNotContain("X-Review", "Review Token");
    }

    @Test void refreshAndLogoutDescribeTheirActualConfiguredHttpOnlyCookieContract() {
        JsonNode cookie = api.path("components").path("securitySchemes").path(ApiSecurityDocumentation.REFRESH_COOKIE_SCHEME);
        assertThat(cookie.path("type").asText()).isEqualTo("apiKey");
        assertThat(cookie.path("in").asText()).isEqualTo("cookie");
        assertThat(cookie.path("name").asText()).isEqualTo("docs-refresh");
        JsonNode refresh = operation("/api/auth/refresh", "post");
        assertThat(refresh.path("security").size()).isEqualTo(1);
        assertThat(refresh.path("security").get(0).has(ApiSecurityDocumentation.REFRESH_COOKIE_SCHEME)).isTrue();
        assertThat(refresh.path("description").asText()).contains("docs-refresh", "rotates", "missing");
        JsonNode logout = operation("/api/auth/logout", "post");
        assertThat(logout.path("security").size()).isEqualTo(2);
        assertThat(logout.path("security").get(0).isEmpty()).isTrue();
        assertThat(logout.path("description").asText()).contains("optional", "Max-Age=0");
    }

    @Test void documentationChangesDoNotChangeActualAnonymousAccessOrCookieEnforcement() throws Exception {
        mvc.perform(get("/api/me/graduation/profile")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
        mvc.perform(get("/api/public/credentials/missing-synthetic-credential")).andExpect(status().isNotFound());
    }

    private JsonNode operation(String path, String method) {
        JsonNode operation = api.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode()).as(method + " " + path + " is a real registered operation").isFalse();
        return operation;
    }
    private void assertNoJwt(String path, String method) {
        JsonNode security = operation(path, method).path("security");
        assertThat(security.isArray()).isTrue(); assertThat(security).isEmpty();
    }
    private void assertJwt(String path, String method) {
        JsonNode security = operation(path, method).path("security");
        assertThat(security.size()).isEqualTo(1);
        assertThat(security.get(0).has(SwaggerConfig.JWT_SCHEME_NAME)).isTrue();
    }
}
