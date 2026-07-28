package com.api.trekkey.domain.credential.crypto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OpenZeppelin StandardMerkleTree V1-compatible binary tree for already double-hashed leaves.
 * The backing array uses OpenZeppelin's complete binary heap layout, including reverse leaf placement.
 */
public final class StandardMerkleTree {

    private final List<Hash32> leaves;
    private final List<Hash32> tree;

    private StandardMerkleTree(List<Hash32> leaves, List<Hash32> tree) {
        this.leaves = leaves;
        this.tree = tree;
    }

    public static StandardMerkleTree fromLeaves(List<CredentialLeaf> credentialLeaves) {
        if (credentialLeaves == null || credentialLeaves.isEmpty()) {
            throw new CryptoValidationException("Merkle tree must contain at least one leaf");
        }
        return fromLeafHashes(credentialLeaves.stream().map(CredentialLeaf::hash).toList());
    }

    public static StandardMerkleTree fromLeafHashes(List<Hash32> inputLeaves) {
        if (inputLeaves == null || inputLeaves.isEmpty()) {
            throw new CryptoValidationException("Merkle tree must contain at least one leaf");
        }
        if (inputLeaves.stream().anyMatch(leaf -> leaf == null)) {
            throw new CryptoValidationException("Merkle tree leaves must not contain null");
        }
        Set<Hash32> distinct = new HashSet<>(inputLeaves);
        if (distinct.size() != inputLeaves.size()) {
            throw new CryptoValidationException("duplicate leaf hashes are not allowed in tree version 1");
        }

        List<Hash32> sortedLeaves = inputLeaves.stream().sorted().toList();
        int treeSize = 2 * sortedLeaves.size() - 1;
        Hash32[] values = new Hash32[treeSize];
        for (int leafIndex = 0; leafIndex < sortedLeaves.size(); leafIndex++) {
            values[treeSize - 1 - leafIndex] = sortedLeaves.get(leafIndex);
        }
        for (int nodeIndex = sortedLeaves.size() - 2; nodeIndex >= 0; nodeIndex--) {
            values[nodeIndex] = hashPair(values[2 * nodeIndex + 1], values[2 * nodeIndex + 2]);
        }
        return new StandardMerkleTree(sortedLeaves, List.of(values));
    }

    public Hash32 root() {
        return tree.getFirst();
    }

    public List<Hash32> leaves() {
        return leaves;
    }

    public int leafIndex(Hash32 leafHash) {
        int index = leaves.indexOf(leafHash);
        if (index < 0) {
            throw new CryptoValidationException("leaf hash does not exist in this Merkle tree");
        }
        return index;
    }

    public List<Hash32> proofForLeaf(Hash32 leafHash) {
        int index = tree.size() - 1 - leafIndex(leafHash);
        java.util.ArrayList<Hash32> proof = new java.util.ArrayList<>();
        while (index > 0) {
            int siblingIndex = index % 2 == 0 ? index - 1 : index + 1;
            if (siblingIndex < tree.size()) {
                proof.add(tree.get(siblingIndex));
            }
            index = (index - 1) / 2;
        }
        return List.copyOf(proof);
    }

    public static boolean verify(Hash32 root, Hash32 leafHash, List<Hash32> proof) {
        if (root == null || leafHash == null || proof == null || proof.stream().anyMatch(value -> value == null)) {
            return false;
        }
        Hash32 computed = leafHash;
        for (Hash32 sibling : proof) {
            computed = hashPair(computed, sibling);
        }
        return root.equals(computed);
    }

    public static Hash32 hashPair(Hash32 first, Hash32 second) {
        if (first == null || second == null) {
            throw new CryptoValidationException("Merkle node values must not be null");
        }
        return first.compareTo(second) <= 0
            ? Hashing.keccak256(HexCodec.concat(first.bytes(), second.bytes()))
            : Hashing.keccak256(HexCodec.concat(second.bytes(), first.bytes()));
    }
}
