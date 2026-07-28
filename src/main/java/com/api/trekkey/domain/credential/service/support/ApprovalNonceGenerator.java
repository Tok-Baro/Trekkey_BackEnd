package com.api.trekkey.domain.credential.service.support;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApprovalNonceGenerator {

    private final SecureRandom secureRandom;

    public long next() {
        long nonce;
        do {
            nonce = secureRandom.nextLong() & Long.MAX_VALUE;
        } while (nonce == 0);
        return nonce;
    }
}
