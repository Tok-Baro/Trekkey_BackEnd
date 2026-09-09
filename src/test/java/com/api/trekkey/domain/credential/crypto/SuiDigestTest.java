package com.api.trekkey.domain.credential.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SuiDigestTest {
    @Test
    void preservesLeadingZeroBytesInCanonicalBase58() {
        assertThat(SuiDigest.encode(new byte[32])).isEqualTo("1".repeat(32));
        assertThat(SuiDigest.decode("1".repeat(32))).isEqualTo(Hash32.ZERO);
        byte[] one = new byte[32];
        one[31] = 1;
        assertThat(SuiDigest.encode(one)).isEqualTo("1".repeat(31) + "2");
        assertThat(SuiDigest.decode("1".repeat(31) + "2").bytes()).containsExactly(one);
    }

    @Test
    void roundTripsAllByteValuesAndHighBitDigestsWithoutSignExtension() {
        for (int value = 0; value <= 255; value++) {
            byte[] bytes = new byte[32];
            Arrays.fill(bytes, (byte) value);
            String encoded = SuiDigest.encode(bytes);
            assertThat(encoded.length()).isBetween(32, 44);
            assertThat(SuiDigest.decode(encoded).bytes()).containsExactly(bytes);
        }
        Random random = new Random(0x535549L);
        for (int zeros = 0; zeros < 32; zeros++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            Arrays.fill(bytes, 0, zeros, (byte) 0);
            assertThat(SuiDigest.decode(SuiDigest.encode(bytes)).bytes()).containsExactly(bytes);
        }
    }

    @Test
    void rejectsInvalidAlphabetLengthWhitespaceAndOverflow() {
        for (String digest : new String[] {null, "", "1".repeat(31), "1".repeat(45), "z".repeat(44),
            "0".repeat(32), "O".repeat(32), "I".repeat(32), "l".repeat(32),
            " " + "1".repeat(32), "1".repeat(31) + "\n", "１".repeat(32), "0x" + "11".repeat(32)}) {
            assertThatThrownBy(() -> SuiDigest.decode(digest)).isInstanceOf(CryptoValidationException.class);
        }
    }

    @Test
    void rejectsNoncanonicalExtraOrMissingLeadingZeros() {
        for (String digest : new String[] {"1".repeat(33), "1".repeat(32) + "2", "2".repeat(32)}) {
            assertThatThrownBy(() -> SuiDigest.decode(digest))
                .isInstanceOf(CryptoValidationException.class).hasMessageContaining("noncanonical");
        }
    }

    @Test
    void encoderRequiresExactlyThirtyTwoBytes() {
        assertThatThrownBy(() -> SuiDigest.encode(null)).isInstanceOf(CryptoValidationException.class);
        for (int size : new int[] {0, 20, 31, 33, 64}) {
            assertThatThrownBy(() -> SuiDigest.encode(new byte[size])).isInstanceOf(CryptoValidationException.class);
        }
    }
}
