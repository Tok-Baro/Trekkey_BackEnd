package com.api.trekkey;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.global.security.jwt.JwtProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/** Checks the real production placeholder file against only the forked test JVM environment. */
class TestRuntimeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    context.getEnvironment().getPropertySources().addLast(
                            new ResourcePropertySource("application-main-placeholders", "classpath:application.properties"));
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
            }).withUserConfiguration(JwtConfiguration.class);

    @Test void absentDeveloperEnvironmentNoLongerLeavesMandatoryTestPlaceholdersUnresolved() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(jwt.getAccessExpiration()).isNotNull();
            assertThat(jwt.getRefreshExpiration()).isNotNull();
            for (String key : new String[]{"app.front.base-url", "app.cors.allowed-origin", "app.file.upload-dir"}) {
                assertThat(context.getEnvironment().getRequiredProperty(key)).doesNotContain("${");
            }
        });
    }

    @Test void explicitSpringTestPropertiesStillOverrideTheProcessFallbacks() {
        runner.withPropertyValues("security.jwt.access-expiration=37", "security.jwt.refresh-expiration=97",
                        "app.file.upload-dir=/synthetic-override-not-opened", "app.front.base-url=http://localhost:45678")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtProperties jwt = context.getBean(JwtProperties.class);
                    assertThat(jwt.getAccessExpiration()).isEqualTo(37);
                    assertThat(jwt.getRefreshExpiration()).isEqualTo(97);
                    assertThat(context.getEnvironment().getRequiredProperty("app.file.upload-dir"))
                            .isEqualTo("/synthetic-override-not-opened");
                    assertThat(context.getEnvironment().getRequiredProperty("app.front.base-url"))
                            .isEqualTo("http://localhost:45678");
                });
    }

    @Test void productionResourcesStillRequireRealRuntimeConfigurationWithoutSyntheticSecretDefaults() throws IOException {
        Properties production = new Properties();
        try (var input = new ClassPathResource("application.properties").getInputStream()) { production.load(input); }
        assertThat(production.getProperty("security.jwt.secret-key")).isEqualTo("${JWT_SECRET_KEY}");
        assertThat(production.getProperty("security.jwt.access-expiration")).isEqualTo("${JWT_ACCESS_EXPIRATION}");
        assertThat(production.getProperty("security.jwt.refresh-expiration")).isEqualTo("${JWT_REFRESH_EXPIRATION}");
        assertThat(production.getProperty("app.file.upload-dir")).isEqualTo("${FILE_UPLOAD_DIR}");
        assertThat(production.toString()).doesNotContain("synthetic-test-only", "synthetic-override");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtConfiguration {}
}
