package com.api.trekkey.domain.credential.crypto;

import java.util.Arrays;

/** Transaction sender/contract coordinates, not an institution's ECDSA signing identity. */
public final class ChainAddress {
    private final byte[] value;

    private ChainAddress(byte[] value) {
        if (value == null || (value.length != 20 && value.length != 32)) {
            throw new CryptoValidationException("chain address must be 20 (EVM) or 32 (Sui) bytes");
        }
        this.value = value.clone();
    }

    public static ChainAddress of(byte[] value) { return new ChainAddress(value); }
    public byte[] bytes() { return value.clone(); }
    public String hex() { return "0x" + java.util.HexFormat.of().formatHex(value); }
    @Override public boolean equals(Object other) {
        return other instanceof ChainAddress address && Arrays.equals(value, address.value);
    }
    @Override public int hashCode() { return Arrays.hashCode(value); }
    @Override public String toString() { return hex(); }
}
