package com.api.trekkey.domain.credential.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "true"
)
@EnabledIfEnvironmentVariable(
        named = "MYSQL_TEST_URL",
        matches = "jdbc:mysql://.+/trekkey_test(?:\\?.*)?"
)
class AncChainTransactionMySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MYSQL_TEST_URL"));
        registry.add(
                "spring.datasource.username",
                () -> environment("MYSQL_TEST_USERNAME", "root"));
        registry.add(
                "spring.datasource.password",
                () -> environment("MYSQL_TEST_PASSWORD", ""));
    }

    @Test
    @DisplayName("MySQL은 255바이트를 넘는 서명 raw transaction을 저장한다")
    void signedRawTransactionSupportsRealTransactionSize() {
        String dataType = jdbcTemplate.queryForObject(
                """
                SELECT DATA_TYPE
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'anc_chain_transaction'
                  AND column_name = 'signed_raw_transaction'
                """,
                String.class);
        assertThat(dataType).isEqualTo("blob");

        byte[] rawTransaction = new byte[1024];
        Arrays.fill(rawTransaction, (byte) 0x5a);
        jdbcTemplate.update(
                """
                INSERT INTO anc_chain_transaction (
                    batch_id,
                    operation_type,
                    idempotency_key,
                    chain_id,
                    contract_address,
                    contract_version,
                    signed_raw_transaction,
                    status
                ) VALUES (
                    1,
                    'ANCHOR_BATCH',
                    'mysql-signed-raw-transaction-size',
                    1001,
                    UNHEX(REPEAT('11', 20)),
                    '1',
                    ?,
                    'PREPARED'
                )
                """,
                rawTransaction);

        Integer storedLength = jdbcTemplate.queryForObject(
                """
                SELECT OCTET_LENGTH(signed_raw_transaction)
                FROM anc_chain_transaction
                WHERE idempotency_key = 'mysql-signed-raw-transaction-size'
                """,
                Integer.class);
        assertThat(storedLength).isEqualTo(rawTransaction.length);
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
