package com.api.trekkey.domain.credential.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChainAddressTest {
    @Test
    void preservesNativeEvmAndSuiWidthsWithoutPaddingOrTruncation() {
        byte[] evmBytes = bytes(20, 0xab);
        byte[] suiBytes = bytes(32, 0xab);
        ChainAddress evm = ChainAddress.of(evmBytes);
        ChainAddress sui = ChainAddress.of(suiBytes);
        assertThat(evm.bytes()).hasSize(20).containsExactly(evmBytes);
        assertThat(sui.bytes()).hasSize(32).containsExactly(suiBytes);
        assertThat(evm.hex()).isEqualTo("0x" + "ab".repeat(20));
        assertThat(sui.hex()).isEqualTo("0x" + "ab".repeat(32));
        assertThat(evm).isNotEqualTo(sui);
        assertThat(evm).isEqualTo(ChainAddress.of(evmBytes));
        assertThat(sui).hasSameHashCodeAs(ChainAddress.of(suiBytes));
        assertThat(sui.toString()).isEqualTo(sui.hex());
    }

    @Test
    void defensivelyCopiesConstructorAndAccessorBytes() {
        byte[] input = bytes(32, 0x22);
        ChainAddress address = ChainAddress.of(input);
        input[0] = 0;
        byte[] returned = address.bytes();
        returned[1] = 0;
        assertThat(address.bytes()).containsExactly(bytes(32, 0x22));
    }

    @Test
    void rejectsEveryNonNativeWidthAndNull() {
        assertThatThrownBy(() -> ChainAddress.of(null)).isInstanceOf(CryptoValidationException.class);
        for (int size : new int[] {0, 1, 19, 21, 31, 33, 64}) {
            assertThatThrownBy(() -> ChainAddress.of(new byte[size]))
                .isInstanceOf(CryptoValidationException.class).hasMessageContaining("20 (EVM) or 32 (Sui)");
        }
    }

    private static byte[] bytes(int size, int value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
