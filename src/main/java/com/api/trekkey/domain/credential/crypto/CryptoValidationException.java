package com.api.trekkey.domain.credential.crypto;

/**
 * Raised when a value cannot be represented by the on-chain credential protocol.
 */
public class CryptoValidationException extends IllegalArgumentException {

    public CryptoValidationException(String message) {
        super(message);
    }

    public CryptoValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
