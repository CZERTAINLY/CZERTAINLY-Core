package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Replaces inlined key material with the contracted redaction envelope, in one pass, before anything else reads the
 * payload.
 *
 * <p>
 * <b>Ordering is the whole security property.</b> The digest is computed and the plaintext dropped before identity,
 * persistence, logging or metrics can observe the payload. The identity digest is carried out-of-band on the result so
 * the material identity chain can use it without any caller ever holding the plaintext. The stored payload carries no
 * digest, and the caller's input is never mutated.
 *
 * <p>
 * The value is hashed verbatim -- no base64 decode, no trim. Normalizing first would make identity depend on the
 * normalizer.
 */
public final class MaterialRedaction {

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

        List<String> findings = new ArrayList<>();
        // Before the value branch, and independent of it. A scanner that fingerprints a detected secret to dedupe its
        // findings emits the same unsalted digest the withhold rule below refuses to publish -- and it does so in a
        // sibling member that the value redaction never touches, on a block that may carry no inlined value at all.
        withholdFingerprint(materialNode, materialType, findings);

        if (!materialNode.has(CbomNames.VALUE)) {
            return new MaterialRedaction(payload, materialType, null, null, null, List.copyOf(findings));
        }
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
        String identityDigest = IdentityDigests.sha256Hex(value);

        ObjectNode redacted = materialNode.objectNode();
        redacted.put("redacted", true);
        redacted.put("length", length);
        String publishedDigest = digestPublishable(materialType) ? identityDigest : null;
        if (publishedDigest == null) {
            // No digest at all. An unsalted SHA-256 of a password or a token is rainbow-table reversible, so
            // publishing it is the same leak one step removed -- and producers really do emit generic-password and
            // jwt-token material. Identity falls through to the occurrence tier, which the chain already has.
            findings.add("digest withheld: " + materialType + " is low-entropy material");
        }
        materialNode.set(CbomNames.VALUE, redacted);
        if (materialType != null && SECRET_TYPES.contains(AsciiText.fold(AsciiText.strip(materialType)))) {
            findings.add("producer inlined a value on material type " + materialType);
        }
        return new MaterialRedaction(payload, materialType, identityDigest, publishedDigest, length, findings);
    }

    /**
     * Fails closed on an absent or blank type, and on every type in {@link #LOW_ENTROPY_TYPES} -- which includes the
     * two types that say the producer did not know either.
     */
    private static boolean digestPublishable(String materialType) {
        // AsciiText, not the JDK. String.isBlank/strip consult Character.isWhitespace, which does not treat
        // U+0085, U+00A0 or U+202F as whitespace -- so a type pasted out of a document as "password\u00A0" kept its
        // trailing no-break space, missed LOW_ENTROPY_TYPES, and published sha256 of the password. A type made only
        // of those code points was likewise non-blank here and took the publish branch, defeating the fail-closed
        // rule for a type that is in the set.
        if (AsciiText.isBlank(materialType)) {
            return false;
        }
        return !LOW_ENTROPY_TYPES.contains(AsciiText.fold(AsciiText.strip(materialType)));
    }

    /**
     * Removes a fingerprint digest of low-entropy material. {@code value} is not the only member that can carry the
     * plaintext's digest: a secret scanner fingerprints what it found so it can dedupe findings across runs, and that
     * digest is exactly as reversible as the one {@link #digestPublishable} refuses to publish.
     */
    private static void withholdFingerprint(ObjectNode materialNode, String materialType, List<String> findings) {
        JsonNode fingerprint = materialNode.get("fingerprint");
        if (fingerprint == null || !fingerprint.isObject() || !fingerprint.has("content")
                || digestPublishable(materialType)) {
            return;
        }
        ((ObjectNode) fingerprint).remove("content");
        findings.add("fingerprint digest withheld: " + materialType + " is low-entropy material");
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

    /** The digest that may be used by internal callers, or {@code null} for low-entropy material. */
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
