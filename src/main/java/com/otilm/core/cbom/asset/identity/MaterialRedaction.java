package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Replaces inlined key material with a digest and a length, in one pass, before anything else reads the payload.
 *
 * <p>
 * <b>Ordering is the whole security property.</b> The digest is computed and the plaintext dropped before identity,
 * persistence, logging or metrics can observe the payload. The identity digest is carried out-of-band on the result so
 * the material identity chain can use it without any caller ever holding the plaintext, and the caller's input is never
 * mutated.
 *
 * <p>
 * The value is hashed verbatim -- no base64 decode, no trim. Normalizing first would make identity depend on the
 * normalizer.
 */
public final class MaterialRedaction {

    public static final String REDACTED_MARKER = "urn:otilm:redacted";

    /**
     * Material types whose plaintext is low-entropy enough that publishing {@code sha256(value)} would itself be an
     * offline dictionary attack served by the platform.
     *
     * <p>
     * {@code other} and {@code unknown} are in the set for the same reason an absent type is: the platform cannot know
     * what a producer put there, and guessing wrong publishes a reversible digest. Withholding is the only protection
     * available, since the stored digest is deliberately unsalted.
     */
    private static final Set<String> LOW_ENTROPY_TYPES = Set.of("password", "token", "credential", "other", "unknown");

    /**
     * Types that should never carry an inlined value at all. A producer that does so has exfiltrated key material into
     * a document the platform then aggregates estate-wide, so it is raised as an ingest finding rather than silently
     * redacted.
     */
    private static final Set<String> SECRET_TYPES = Set
            .of("private-key", "secret-key", "shared-secret", "password", "credential", "token", "seed", "key");

    private final ObjectNode payload;
    private final String materialType;
    private final String identityDigest;
    private final String publishedDigest;
    private final Integer valueLength;
    private final List<String> findings;

    private MaterialRedaction(ObjectNode payload, String materialType, String identityDigest, String publishedDigest,
            Integer valueLength, List<String> findings) {
        this.payload = payload;
        this.materialType = materialType;
        this.identityDigest = identityDigest;
        this.publishedDigest = publishedDigest;
        this.valueLength = valueLength;
        this.findings = List.copyOf(findings);
    }

    /**
     * Redacts a component's {@code cryptoProperties}, returning a copy. The argument is left untouched.
     *
     * @param cryptoProperties the raw properties block, which may be {@code null} for a component that carries none
     */
    public static MaterialRedaction of(JsonNode cryptoProperties) {
        ObjectNode payload = cryptoProperties == null || !cryptoProperties.isObject()
                ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                : cryptoProperties.deepCopy();
        JsonNode material = payload.get(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES);
        if (material != null && !material.isObject()) {
            // Dropped, not passed through. A producer emitting the block as an array or a string put key material
            // somewhere no redaction step reads, and the payload is both stored and hashed into the backstop
            // pre-image -- so passing it through retained the plaintext this class exists to remove. The non-object
            // `cryptoProperties` case one line above already failed closed; this one did not.
            payload.remove(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES);
            return new MaterialRedaction(payload, null, null, null, null, List.of("non-object material block dropped"));
        }
        if (material == null) {
            return new MaterialRedaction(payload, null, null, null, null, List.of());
        }
        ObjectNode materialNode = (ObjectNode) material;
        JsonNode typeNode = materialNode.get("type");
        String materialType = typeNode != null && typeNode.isTextual() ? typeNode.textValue() : null;
        if (!materialNode.has(CbomNames.VALUE)) {
            return new MaterialRedaction(payload, materialType, null, null, null, List.of());
        }

        List<String> findings = new ArrayList<>();
        JsonNode raw = materialNode.get(CbomNames.VALUE);
        if (raw == null || !raw.isTextual()) {
            // A non-string value cannot be hashed meaningfully and must not survive.
            materialNode.remove(CbomNames.VALUE);
            findings.add("non-string material value dropped");
            return new MaterialRedaction(payload, materialType, null, null, null, findings);
        }

        String value = raw.textValue();
        // Code points, not UTF-16 units. The reference counts characters, so a material value carrying anything
        // outside the basic multilingual plane would otherwise be reported one length here and another there -- and
        // the length is served back in the stored payload.
        int length = value.codePointCount(0, value.length());
        // The identity digest exists for EVERY value, including low-entropy ones: the identity key is a hash of the
        // whole pre-image and is never exposed on any API, so using it costs nothing and keeps two different
        // passwords at one source coordinate apart. What is withheld for low-entropy material is the digest in the
        // STORED payload, which is served back and would be a reversible password hash.
        String identityDigest = Digests.sha256Hex(value);

        ObjectNode redacted = materialNode.objectNode();
        redacted.put("$redacted", REDACTED_MARKER);
        String publishedDigest = null;
        if (digestPublishable(materialType)) {
            publishedDigest = identityDigest;
            redacted.put("sha256", publishedDigest);
            redacted.put("length", length);
        } else {
            // No digest at all. An unsalted SHA-256 of a password or a token is rainbow-table reversible, so
            // publishing it is the same leak one step removed -- and producers really do emit generic-password and
            // jwt-token material. Identity falls through to the occurrence tier, which the chain already has.
            redacted.put("length", length);
            findings.add("digest withheld: " + materialType + " is low-entropy material");
        }
        materialNode.set(CbomNames.VALUE, redacted);
        if (materialType != null && SECRET_TYPES.contains(AsciiText.fold(materialType))) {
            findings.add("producer inlined a value on material type " + materialType);
        }
        return new MaterialRedaction(payload, materialType, identityDigest, publishedDigest, length, findings);
    }

    /**
     * Fails closed on an absent or blank type, and on every type in {@link #LOW_ENTROPY_TYPES} -- which includes the
     * two types that say the producer did not know either.
     */
    private static boolean digestPublishable(String materialType) {
        if (materialType == null || materialType.isBlank()) {
            return false;
        }
        return !LOW_ENTROPY_TYPES.contains(AsciiText.fold(materialType.strip()));
    }

    /** The redacted properties. This is what may be stored, keyed, logged or served. */
    public ObjectNode payload() {
        return payload;
    }

    /**
     * The digest of the plaintext, for the material identity chain only.
     *
     * <p>
     * Present even when {@link #publishedDigest()} is withheld. It must never reach a stored payload or a wire response
     * -- it is a hash of a possibly low-entropy secret, and the identity key that consumes it is itself fenced from
     * every client-facing surface.
     */
    public String identityDigest() {
        return identityDigest;
    }

    /** The digest that may appear in the stored payload, or {@code null} for low-entropy material. */
    public String publishedDigest() {
        return publishedDigest;
    }

    public Integer valueLength() {
        return valueLength;
    }

    public String materialType() {
        return materialType;
    }

    /** Ingest findings worth reporting: a dropped value, a withheld digest, or a producer leaking key material. */
    public List<String> findings() {
        return findings;
    }
}
