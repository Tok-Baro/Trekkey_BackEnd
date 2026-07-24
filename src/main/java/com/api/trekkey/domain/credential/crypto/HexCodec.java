package com.api.trekkey.domain.credential.crypto;

import java.util.Arrays;

final class HexCodec {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HexCodec() {
    }

    static byte[] decode(String value, int expectedLength, String fieldName) {
        if (value == null || !value.startsWith("0x")) {
            throw new CryptoValidationException(fieldName + " must be a 0x-prefixed hex value");
        }

        String raw = value.substring(2);
        if (raw.length() != expectedLength * 2) {
            throw new CryptoValidationException(fieldName + " must contain exactly " + expectedLength + " bytes");
        }

        byte[] decoded = new byte[expectedLength];
        for (int index = 0; index < raw.length(); index += 2) {
            int high = Character.digit(raw.charAt(index), 16);
            int low = Character.digit(raw.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new CryptoValidationException(fieldName + " contains non-hex characters");
            }
            decoded[index / 2] = (byte) ((high << 4) | low);
        }
        return decoded;
    }

    static String encode(byte[] value) {
        char[] result = new char[value.length * 2 + 2];
        result[0] = '0';
        result[1] = 'x';
        for (int index = 0; index < value.length; index++) {
            int unsigned = value[index] & 0xff;
            result[index * 2 + 2] = HEX[unsigned >>> 4];
            result[index * 2 + 3] = HEX[unsigned & 0x0f];
        }
        return new String(result);
    }

    static byte[] concat(byte[]... values) {
        int length = Arrays.stream(values).mapToInt(value -> value.length).sum();
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }
}
