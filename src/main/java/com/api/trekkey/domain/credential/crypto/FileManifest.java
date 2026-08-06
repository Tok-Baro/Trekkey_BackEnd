package com.api.trekkey.domain.credential.crypto;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.List;

/** Canonical, content-addressed representation of files attached to a credential. */
public final class FileManifest {

    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private FileManifest() {
    }

    public static Result build(List<Entry> entries) {
        if (entries == null) {
            throw new CryptoValidationException("file manifest entries must not be null");
        }
        List<Entry> sortedEntries = entries.stream()
            .map(Entry::normalized)
            .sorted(Comparator.comparing(Entry::sha256)
                .thenComparingLong(Entry::sizeBytes)
                .thenComparing(Entry::contentType)
                .thenComparing(Entry::originalName))
            .toList();

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("manifestVersion", 1);
        ArrayNode files = root.putArray("files");
        for (Entry entry : sortedEntries) {
            ObjectNode file = files.addObject();
            file.put("contentType", entry.contentType());
            file.put("originalName", entry.originalName());
            file.put("sha256Hex", entry.sha256().hex());
            file.put("sizeBytes", entry.sizeBytes());
        }
        byte[] canonicalBytes = CanonicalJson.canonicalize(root);
        return new Result(List.copyOf(sortedEntries), canonicalBytes, Hashing.sha256(canonicalBytes));
    }

    public record Entry(String originalName, String contentType, long sizeBytes, Hash32 sha256) {

        public Entry {
            if (originalName == null || originalName.isBlank()) {
                throw new CryptoValidationException("file originalName must not be blank");
            }
            if (contentType == null || contentType.isBlank()) {
                throw new CryptoValidationException("file contentType must not be blank");
            }
            if (sizeBytes < 0 || sizeBytes > MAX_SAFE_INTEGER) {
                throw new CryptoValidationException("file size must be a non-negative safe integer");
            }
            if (sha256 == null) {
                throw new CryptoValidationException("file sha256 must not be null");
            }
        }

        private Entry normalized() {
            return new Entry(
                CanonicalJson.normalizeNfc(originalName, "file originalName"),
                CanonicalJson.normalizeNfc(contentType, "file contentType"),
                sizeBytes,
                sha256
            );
        }
    }

    public record Result(List<Entry> entries, byte[] canonicalBytes, Hash32 hash) {

        public Result {
            entries = List.copyOf(entries);
            canonicalBytes = canonicalBytes.clone();
        }

        @Override
        public byte[] canonicalBytes() {
            return canonicalBytes.clone();
        }
    }
}
