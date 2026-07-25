package com.api.trekkey.domain.credential.crypto;

import java.math.BigInteger;
import java.util.Arrays;

/** Immutable 20-byte EVM address. */
public final class EthereumAddress {

    public static final int LENGTH = 20;

    private final byte[] bytes;

    private EthereumAddress(byte[] bytes) {
        this.bytes = bytes;
    }

    public static EthereumAddress fromHex(String value) {
        return new EthereumAddress(HexCodec.decode(value, LENGTH, "Ethereum address"));
    }

    public static EthereumAddress fromBytes(byte[] value) {
        if (value == null || value.length != LENGTH) {
            throw new CryptoValidationException("Ethereum address must be 20 bytes");
        }
        return new EthereumAddress(Arrays.copyOf(value, LENGTH));
    }

    public static EthereumAddress fromPublicKey(BigInteger publicKey) {
        String address = org.web3j.crypto.Keys.getAddress(publicKey);
        return fromHex("0x" + address);
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, LENGTH);
    }

    public String hex() {
        return HexCodec.encode(bytes);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof EthereumAddress other && Arrays.equals(bytes, other.bytes);
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
