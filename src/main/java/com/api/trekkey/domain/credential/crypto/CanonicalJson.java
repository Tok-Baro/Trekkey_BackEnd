package com.api.trekkey.domain.credential.crypto;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.erdtman.jcs.JsonCanonicalizer;

/** RFC 8785 JCS canonicalization after Trekkey's NFC and integer-only validation rules. */
public final class CanonicalJson {

    private static final BigInteger MAX_SAFE_INTEGER = BigInteger.valueOf(9_007_199_254_740_991L);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build());

    private CanonicalJson() {
    }

    public static byte[] canonicalize(String json) {
        if (json == null || json.isBlank()) {
            throw new CryptoValidationException("JSON payload must not be blank");
        }
        try {
            return canonicalize(OBJECT_MAPPER.readTree(json));
        } catch (IOException exception) {
            throw new CryptoValidationException("invalid JSON payload", exception);
        }
    }

    public static byte[] canonicalize(JsonNode source) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            throw new CryptoValidationException("JSON payload must be an object or array");
        }
        if (!source.isObject() && !source.isArray()) {
            throw new CryptoValidationException("JSON root must be an object or array");
        }
        try {
            JsonNode normalized = normalize(source);
            String input = OBJECT_MAPPER.writeValueAsString(normalized);
            return new JsonCanonicalizer(input).getEncodedUTF8();
        } catch (IOException exception) {
            throw new CryptoValidationException("could not canonicalize JSON payload", exception);
        }
    }

    public static String normalizeNfc(String value, String fieldName) {
        if (value == null) {
            throw new CryptoValidationException(fieldName + " must not be null");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    private static JsonNode normalize(JsonNode source) {
        if (source.isObject()) {
            ObjectNode target = JsonNodeFactory.instance.objectNode();
            Set<String> normalizedNames = new HashSet<>();
            for (Map.Entry<String, JsonNode> field : source.properties()) {
                String normalizedName = normalizeNfc(field.getKey(), "JSON object key");
                if (!normalizedNames.add(normalizedName)) {
                    throw new CryptoValidationException("duplicate JSON object key after NFC normalization: " + normalizedName);
                }
                target.set(normalizedName, normalize(field.getValue()));
            }
            return target;
        }
        if (source.isArray()) {
            ArrayNode target = JsonNodeFactory.instance.arrayNode();
            for (JsonNode value : source) {
                target.add(normalize(value));
            }
            return target;
        }
        if (source.isTextual()) {
            return JsonNodeFactory.instance.textNode(normalizeNfc(source.textValue(), "JSON string"));
        }
        if (source.isFloatingPointNumber()) {
            throw new CryptoValidationException("floating-point JSON numbers are not allowed; use a string for decimal values");
        }
        if (source.isIntegralNumber()) {
            BigInteger value = source.bigIntegerValue();
            if (value.abs().compareTo(MAX_SAFE_INTEGER) > 0) {
                throw new CryptoValidationException("integral JSON numbers must fit the IEEE-754 safe integer range");
            }
            return JsonNodeFactory.instance.numberNode(value);
        }
        if (source.isBoolean() || source.isNull()) {
            return source;
        }
        throw new CryptoValidationException("unsupported JSON value");
    }

    static String utf8(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
