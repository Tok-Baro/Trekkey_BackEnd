package com.api.trekkey.domain.evidence.support;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EvidenceClaimHasher {
    private final byte[] secret;

    public EvidenceClaimHasher(@Value("${app.evidence.lookup-hmac-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("app.evidence.lookup-hmac-secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String normalizedValue) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
