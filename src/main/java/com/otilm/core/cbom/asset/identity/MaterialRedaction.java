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
        dropUncontractedMembers(materialNode, materialType, findings);

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
            // The envelope carries no digest for any type, so this gate no longer decides what is stored -- it decides
            // what publishedDigest() may hand an internal caller. An unsalted SHA-256 of a password or a token is
            // rainbow-table reversible, so a caller that put one on an API would leak it one step removed, and
            // producers really do emit generic-password and jwt-token material.
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
     * The members of {@code relatedCryptoMaterialProperties} this pipeline keeps.
     *
     * <p>
     * Everything else is a producer extension. For low-entropy material the extensions are dropped rather than
     * enumerated, because {@code value} is not the only member that can carry the plaintext's digest and the set of
     * names that can is open: a secret scanner fingerprints what it found so it can dedupe findings across runs, and
     * that digest is exactly as reversible as the one {@link #digestPublishable} refuses to publish.
     *
     * <p>
     * <b>One entry is not contracted, and is kept anyway.</b> {@code relatedCryptoMaterialType} appears in neither the
     * CycloneDX 1.6 nor the 1.7 {@code relatedCryptoMaterialProperties} schema, and no code here reads it -- the
     * material type is read from {@code type}. It is an extension by every available definition, so the paragraph above
     * does not describe it.
     *
     * <p>
     * It is listed because dropping it moved a ratified identity key. The backstop pre-image ends in a projection
     * digest over this payload, so removing any member re-keys the row, and vector {@code gen-068-mat-backstop} expects
     * the member present. That makes this entry a stopgap for a layer error rather than a statement about the contract:
     * the drop should never have reached the hashed projection at all, since the specification enumerates exactly which
     * fields are stripped before a hash and this is not among them. Until the payload that is keyed is separated from
     * the payload that is stored, the enumeration has to carry it.
     */
    private static final Set<String> CONTRACTED_MEMBERS = Set
            .of("type", "relatedCryptoMaterialType", "id", "state", "algorithmRef", "creationDate", "activationDate",
                    "updateDate", "expirationDate", "value", "size", "format", "securedBy");

    /**
     * Drops every uncontracted member of low-entropy material, and says which.
     *
     * <p>
     * <b>An allowlist, because the hazard is open-ended.</b> The predecessor named {@code fingerprint} and
     * {@code digest} and withheld those two: {@code hash}, {@code hashes}, {@code sha256}, {@code checksum},
     * {@code thumbprint}, {@code md5}, {@code fingerprints} and even {@code Fingerprint} -- the match was
     * case-sensitive -- each carried an unsalted SHA-256 of a password into the served payload with no finding raised.
     * None of those is a CycloneDX field, which is what makes enumeration the wrong shape of defence: the next scanner
     * invents the eleventh name and it fails open again, silently, exactly as five of six spellings did before.
     *
     * <p>
     * Inverting it costs a producer's harmless extensions on low-entropy material only, and costs them loudly -- the
     * finding names every member removed, so nothing disappears without a record. This is the same instinct as dropping
     * an unrecognised value <em>shape</em> rather than trusting it, applied to the member name.
     */
    private static void dropUncontractedMembers(ObjectNode materialNode, String materialType, List<String> findings) {
        if (digestPublishable(materialType)) {
            return;
        }
        List<String> dropped = new ArrayList<>();
        materialNode.fieldNames().forEachRemaining(name -> {
            if (!CONTRACTED_MEMBERS.contains(name)) {
                dropped.add(name);
            }
        });
        if (dropped.isEmpty()) {
            return;
        }
        dropped.sort(AsciiText.BY_CODE_POINT);
        dropped.forEach(materialNode::remove);
        findings
                .add("uncontracted members dropped for low-entropy material, any of which may carry a reversible "
                        + "digest of the plaintext: " + String.join(", ", dropped));
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
