package com.otilm.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.api.exception.ValidationException;
import com.otilm.core.oid.OidHandler;
import com.otilm.core.serialization.ObjectMapperFactory;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes a structural ASN.1 JSON tree into DER. Each node is an object with exactly one key naming its ASN.1 type;
 * composite nodes ({@code sequence}, {@code set}, {@code tagged}) nest further nodes. This parser is the dialect's
 * grammar: what it accepts is the grammar, and a value it rejects names the offending node's JSON path.
 */
public final class AsnJsonCodec {

    // ObjectMapperFactory is the single home of production mapper recipes; reading a JSON tree needs
    // nothing beyond the wire recipe.
    private static final Logger logger = LoggerFactory.getLogger(AsnJsonCodec.class);
    private static final ObjectMapper MAPPER = ObjectMapperFactory.wire();

    private AsnJsonCodec() {
    }

    /**
     * Parses a tree value. This is the one place the dialect's text form is read, so a caller that needs both the tree
     * and its encoding parses once and cannot end up judging a different tree than it encodes.
     */
    public static JsonNode parse(String json) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new ValidationException("Extension value is not well-formed JSON");
        }
        if (tree == null || !tree.isObject()) {
            throw new ValidationException("Extension value is not well-formed JSON: expected an object node");
        }
        return tree;
    }

    /** Parses {@code json} and encodes it; a string that is not JSON at all is rejected up front. */
    public static byte[] encodeFromString(String json) {
        return encode(parse(json));
    }

    public static byte[] encode(JsonNode tree) {
        try {
            return toAsn1(tree, "$").toASN1Primitive().getEncoded(ASN1Encoding.DER);
        } catch (IOException e) {
            throw new ValidationException("Extension value could not be encoded to DER");
        } catch (ValidationException e) {
            throw e;
        } catch (RuntimeException e) {
            // BouncyCastle rejects some values with its own unchecked exceptions, and one escaping here would
            // be a 500 rather than a rejection. Its message names library internals and this one reaches
            // request clients, so only the controlled text goes out; every grammar failure already carries its
            // own path-bearing message through the catch above. Logged because reaching here means either an
            // input shape the grammar should have named, or a defect.
            logger.warn("Encoding an ASN.1 JSON tree failed outside the grammar's own checks", e);
            throw new ValidationException("Extension value could not be encoded to DER");
        }
    }

    private static ASN1Encodable toAsn1(JsonNode node, String path) {
        if (!node.isObject() || node.size() != 1) {
            throw new ValidationException(
                    "Node at %s must be an object with exactly one key naming its ASN.1 type".formatted(path));
        }
        Map.Entry<String, JsonNode> entry = node.properties().iterator().next();
        String type = entry.getKey();
        JsonNode value = entry.getValue();
        return switch (type) {
            case "boolean" -> ASN1Boolean.getInstance(requireBoolean(value, path + ".boolean"));
            case "integer" -> new ASN1Integer(requireInteger(value, path + ".integer"));
            case "oid" -> parseOid(value, path + ".oid");
            case "utf8String" -> new DERUTF8String(requireText(value, path + ".utf8String"));
            // The one-arg BC constructors skip charset validation and would emit DER that is invalid
            // for the declared type; the two-arg form checks it.
            case "ia5String" -> ia5String(requireText(value, path + ".ia5String"), path + ".ia5String");
            case "printableString" ->
                printableString(requireText(value, path + ".printableString"), path + ".printableString");
            case "octetString" -> new DEROctetString(requireBase64(value, path + ".octetString"));
            case "bitString" -> parseBitString(value, path + ".bitString");
            case "generalizedTime" ->
                generalizedTime(requireText(value, path + ".generalizedTime"), path + ".generalizedTime");
            case "null" -> derNull(value, path + ".null");
            case "sequence" -> new DERSequence(children(value, path + ".sequence"));
            // DER orders SET components by their encoding, so BouncyCastle sorts here and the declared order of a
            // set's members is not preserved. That is the encoding rule, not a normalisation this codec chose.
            case "set" -> new DERSet(children(value, path + ".set"));
            case "tagged" -> parseTagged(value, path + ".tagged");
            default -> throw new ValidationException("Unknown node type '%s' at %s".formatted(type, path));
        };
    }

    /**
     * ASN.1 NULL carries no content, so the only value that can mean anything is JSON {@code null}. Anything else is an
     * author's mistake — accepting it silently would encode {@code 05 00} and discard what they wrote.
     */
    private static ASN1Encodable derNull(JsonNode value, String path) {
        if (!value.isNull()) {
            throw new ValidationException("Node at %s must be null; ASN.1 NULL carries no value".formatted(path));
        }
        return DERNull.INSTANCE;
    }

    private static ASN1EncodableVector children(JsonNode array, String path) {
        if (!array.isArray()) {
            throw new ValidationException("Node at %s must be an array of nodes".formatted(path));
        }
        ASN1EncodableVector vector = new ASN1EncodableVector();
        int index = 0;
        for (JsonNode child : array) {
            vector.add(toAsn1(child, "%s[%d]".formatted(path, index++)));
        }
        return vector;
    }

    private static ASN1Encodable parseTagged(JsonNode node, String path) {
        if (!node.isObject() || !node.has("tagNo") || !node.has("value")) {
            throw new ValidationException("Node at %s must carry tagNo and value".formatted(path));
        }
        rejectUnknownMembers(node, path, Set.of("tagNo", "explicit", "value"));
        int tagNo = requireBoundedInt(node.get("tagNo"), path + ".tagNo", 0, 30);
        boolean explicit = !node.has("explicit") || requireBoolean(node.get("explicit"), path + ".explicit");
        return new DERTaggedObject(explicit, tagNo, toAsn1(node.get("value"), path + ".value"));
    }

    private static ASN1Encodable parseOid(JsonNode value, String path) {
        String oid = requireText(value, path);
        if (!OidHandler.isOid(oid)) {
            throw new ValidationException("Value at %s is not a dotted-decimal OID".formatted(path));
        }
        return new ASN1ObjectIdentifier(oid);
    }

    private static ASN1Encodable parseBitString(JsonNode node, String path) {
        if (!node.isObject() || !node.has("value")) {
            throw new ValidationException("Node at %s must carry a base64 value and optional padBits".formatted(path));
        }
        rejectUnknownMembers(node, path, Set.of("value", "padBits"));
        byte[] bytes = requireBase64(node.get("value"), path + ".value");
        int padBits = node.has("padBits") ? requireBoundedInt(node.get("padBits"), path + ".padBits", 0, 7) : 0;
        if (bytes.length == 0 && padBits != 0) {
            throw new ValidationException("Value at %s has no content, so padBits must be 0".formatted(path));
        }
        return new DERBitString(bytes, padBits);
    }

    private static boolean requireBoolean(JsonNode node, String path) {
        if (!node.isBoolean()) {
            throw new ValidationException("Value at %s must be a JSON boolean".formatted(path));
        }
        return node.booleanValue();
    }

    private static ASN1Encodable ia5String(String value, String path) {
        try {
            return new DERIA5String(value, true);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Value at %s is not a valid IA5String".formatted(path));
        }
    }

    private static ASN1Encodable printableString(String value, String path) {
        try {
            return new DERPrintableString(value, true);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Value at %s is not a valid PrintableString".formatted(path));
        }
    }

    private static ASN1Encodable generalizedTime(String value, String path) {
        try {
            return new DERGeneralizedTime(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    "Value at %s is not a GeneralizedTime such as 20261231235959Z".formatted(path));
        }
    }

    /**
     * Rejects a member the grammar does not define. Without this a misspelling — {@code explict}, {@code padbits} —
     * would fall back to the member's default and silently encode something other than what was written.
     */
    private static void rejectUnknownMembers(JsonNode node, String path, Set<String> allowed) {
        for (Map.Entry<String, JsonNode> member : node.properties()) {
            if (!allowed.contains(member.getKey())) {
                throw new ValidationException("Node at %s has no member '%s'".formatted(path, member.getKey()));
            }
        }
    }

    /** An integer node constrained to a range, so an oversized value is a validation error and not an overflow. */
    private static int requireBoundedInt(JsonNode node, String path, int min, int max) {
        BigInteger value = requireInteger(node, path);
        if (value.compareTo(BigInteger.valueOf(min)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0) {
            throw new ValidationException("Value at %s must be between %d and %d".formatted(path, min, max));
        }
        return value.intValueExact();
    }

    private static BigInteger requireInteger(JsonNode node, String path) {
        if (!node.isIntegralNumber()) {
            throw new ValidationException("Value at %s must be a JSON integer".formatted(path));
        }
        return node.bigIntegerValue();
    }

    private static String requireText(JsonNode node, String path) {
        if (!node.isTextual()) {
            throw new ValidationException("Value at %s must be a JSON string".formatted(path));
        }
        return node.textValue();
    }

    private static byte[] requireBase64(JsonNode node, String path) {
        try {
            return Base64.getDecoder().decode(requireText(node, path));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Value at %s is not valid base64".formatted(path));
        }
    }
}
