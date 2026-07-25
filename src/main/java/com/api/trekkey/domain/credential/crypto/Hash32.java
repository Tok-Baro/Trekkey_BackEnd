package com.api.trekkey.domain.credential.crypto;

import java.util.Arrays;

/** Immutable 32-byte protocol value. */
public final class Hash32 implements Comparable<Hash32> {

    public static final int LENGTH = 32;
    public static final Hash32 ZERO = new Hash32(new byte[LENGTH]);

    private final byte[] bytes;

    private Hash32(byte[] bytes) {
        this.bytes = bytes;
    }

    public static Hash32 of(byte[] value) {
        if (value == null || value.length != LENGTH) {
            throw new CryptoValidationException("hash must contain exactly 32 bytes");
        }
        return new Hash32(Arrays.copyOf(value, LENGTH));
    }

    public static Hash32 fromHex(String value) {
        return new Hash32(HexCodec.decode(value, LENGTH, "hash"));
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, LENGTH);
    }

    public String hex() {
        return HexCodec.encode(bytes);
    }

    public boolean isZero() {
        return equals(ZERO);
    }

    @Override
    public int compareTo(Hash32 other) {
        for (int index = 0; index < LENGTH; index++) {
            int comparison = Integer.compare(bytes[index] & 0xff, other.bytes[index] & 0xff);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Hash32 other && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return hex();
    }
}
