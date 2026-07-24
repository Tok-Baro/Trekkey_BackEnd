package com.api.trekkey.domain.credential.crypto;

import java.math.BigInteger;
import java.util.Arrays;

/** Encodes the fixed-width values used by the registry's Solidity abi.encode calls. */
final class AbiWords {

    private static final BigInteger UINT_256_MAX = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    private AbiWords() {
    }

    static byte[] encode(Hash32... values) {
        byte[] result = new byte[values.length * Hash32.LENGTH];
        for (int index = 0; index < values.length; index++) {
            System.arraycopy(values[index].bytes(), 0, result, index * Hash32.LENGTH, Hash32.LENGTH);
        }
        return result;
    }

    static byte[] encodeWords(byte[]... words) {
        byte[] result = new byte[words.length * Hash32.LENGTH];
        for (int index = 0; index < words.length; index++) {
            if (words[index].length != Hash32.LENGTH) {
                throw new CryptoValidationException("ABI values must be encoded as 32-byte words");
            }
            System.arraycopy(words[index], 0, result, index * Hash32.LENGTH, Hash32.LENGTH);
        }
        return result;
    }

    static byte[] uint(BigInteger value, int bitLength, String fieldName) {
        if (value == null || value.signum() < 0 || value.bitLength() > bitLength) {
            throw new CryptoValidationException(fieldName + " must fit uint" + bitLength);
        }
        return leftPad(value.toByteArray(), Hash32.LENGTH);
    }

    static byte[] uint256(BigInteger value, String fieldName) {
        if (value == null || value.signum() < 0 || value.compareTo(UINT_256_MAX) > 0) {
            throw new CryptoValidationException(fieldName + " must fit uint256");
        }
        return leftPad(value.toByteArray(), Hash32.LENGTH);
    }

    static byte[] address(EthereumAddress address) {
        return leftPad(address.bytes(), Hash32.LENGTH);
    }

    private static byte[] leftPad(byte[] value, int length) {
        byte[] unsigned = value.length > 1 && value[0] == 0 ? Arrays.copyOfRange(value, 1, value.length) : value;
        if (unsigned.length > length) {
            throw new CryptoValidationException("ABI value exceeds 32 bytes");
        }
        byte[] result = new byte[length];
        System.arraycopy(unsigned, 0, result, length - unsigned.length, unsigned.length);
        return result;
    }
}
