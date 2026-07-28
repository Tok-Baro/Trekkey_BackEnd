package com.api.trekkey.domain.credential.crypto;

import java.math.BigInteger;
import java.util.Arrays;

/** Canonical 65-byte secp256k1 signature encoded as r || s || v. */
public final class Signature65 {

    public static final int LENGTH = 65;

    private static final BigInteger CURVE_ORDER =
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    private static final BigInteger HALF_CURVE_ORDER =
            new BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16);

    private final byte[] bytes;

    private Signature65(byte[] bytes) {
        this.bytes = canonicalize(bytes);
    }

    public static Signature65 fromHex(String value) {
        return new Signature65(HexCodec.decode(value, LENGTH, "signature"));
    }

    public static Signature65 of(byte[] value) {
        if (value == null || value.length != LENGTH) {
            throw new CryptoValidationException("signature must contain exactly 65 bytes");
        }
        return new Signature65(Arrays.copyOf(value, LENGTH));
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, LENGTH);
    }

    public String hex() {
        return HexCodec.encode(bytes);
    }

    byte recoveryId() {
        return bytes[64];
    }

    byte[] r() {
        return Arrays.copyOfRange(bytes, 0, 32);
    }

    byte[] s() {
        return Arrays.copyOfRange(bytes, 32, 64);
    }

    private static byte[] canonicalize(byte[] value) {
        if (value == null || value.length != LENGTH) {
            throw new CryptoValidationException("signature must contain exactly 65 bytes");
        }
        byte[] canonical = Arrays.copyOf(value, LENGTH);
        int recoveryId = canonical[64] & 0xff;
        if (recoveryId == 0 || recoveryId == 1) {
            canonical[64] = (byte) (recoveryId + 27);
        } else if (recoveryId != 27 && recoveryId != 28) {
            throw new CryptoValidationException("signature recovery id must be 0, 1, 27, or 28");
        }

        BigInteger r = new BigInteger(1, Arrays.copyOfRange(canonical, 0, 32));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(canonical, 32, 64));
        if (r.signum() == 0 || r.compareTo(CURVE_ORDER) >= 0) {
            throw new CryptoValidationException("signature r must be in the secp256k1 scalar range");
        }
        if (s.signum() == 0 || s.compareTo(HALF_CURVE_ORDER) > 0) {
            throw new CryptoValidationException("signature s must use canonical low-s form");
        }
        return canonical;
    }
}
