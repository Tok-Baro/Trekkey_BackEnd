package com.api.trekkey.domain.credential.crypto;

import java.math.BigInteger;
import java.util.Arrays;

/** Lossless base58 presentation for Sui's 32-byte transaction/checkpoint digests. */
public final class SuiDigest {
    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);
    private SuiDigest() {}

    public static String encode(byte[] bytes) {
        Hash32.of(bytes);
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder result = new StringBuilder();
        while (number.signum() > 0) {
            BigInteger[] part = number.divideAndRemainder(BASE);
            result.append(ALPHABET.charAt(part[1].intValue()));
            number = part[0];
        }
        for (byte value : bytes) { if (value != 0) break; result.append('1'); }
        return result.reverse().toString();
    }

    public static Hash32 decode(String digest) {
        if (digest == null || digest.length() < 32 || digest.length() > 44) {
            throw new CryptoValidationException("invalid Sui digest");
        }
        BigInteger number = BigInteger.ZERO;
        for (char character : digest.toCharArray()) {
            int digit = ALPHABET.indexOf(character);
            if (digit < 0) throw new CryptoValidationException("invalid Sui digest");
            number = number.multiply(BASE).add(BigInteger.valueOf(digit));
        }
        byte[] encoded = number.toByteArray();
        if (encoded.length == 33 && encoded[0] == 0) encoded = Arrays.copyOfRange(encoded, 1, 33);
        if (encoded.length > 32) throw new CryptoValidationException("invalid Sui digest length");
        byte[] result = new byte[32];
        System.arraycopy(encoded, 0, result, 32 - encoded.length, encoded.length);
        if (!encode(result).equals(digest)) throw new CryptoValidationException("noncanonical Sui digest");
        return Hash32.of(result);
    }
}
