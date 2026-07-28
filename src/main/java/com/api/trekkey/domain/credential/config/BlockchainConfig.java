package com.api.trekkey.domain.credential.config;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(BlockchainProperties.class)
public class BlockchainConfig {

    @Bean
    public SecureRandom approvalSecureRandom() {
        return new SecureRandom();
    }
}
