package com.api.trekkey.domain.credential.service.port;

import lombok.Getter;

@Getter
public class BlockchainGatewayException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public BlockchainGatewayException(
            String errorCode,
            boolean retryable,
            String message,
            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public BlockchainGatewayException(String errorCode, boolean retryable, String message) {
        this(errorCode, retryable, message, null);
    }
}
