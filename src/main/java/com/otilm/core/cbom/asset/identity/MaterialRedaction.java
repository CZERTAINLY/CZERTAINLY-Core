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

    private final ObjectNode keyedPayload;
    private final ObjectNode storedPayload;
    private final String materialType;
    private final String identityDigest;
    private final String publishedDigest;
    private final Integer valueLength;
    private final List<String> findings;

    private MaterialRedaction(ObjectNode keyedPayload, ObjectNode storedPayload, String materialType,
            String identityDigest, String publishedDigest, Integer valueLength, List<String> findings) {
        this.keyedPayload = keyedPayload;
        this.storedPayload = storedPayload;
        this.materialType = materialType;
        this.identityDigest = identityDigest;
        this.publishedDigest = publishedDigest;
        this.valueLength = valueLength;
        this.findings = List.copyOf(findings);
    }

    /**
     * Redacts a component's {@code cryptoProperties}, returning two copies. The argument is left untouched.
     *
     * <p>
     * <b>Two payloads, because storage and identity answer to different rules.</b> R2 enumerates exactly what is
     * stripped before any hash -- the five document-internal reference fields -- and R15 fixes the canonical form of
     * what remains, so a member this class chooses to withhold from storage cannot be allowed to move the key. Dropping
     * members from the single shared payload did exactly that: the hashed projection lost every uncontracted member,
     * which moved {@code mat:backstop} away from the reference for any material carrying one. Measured on the
     * 2026-08-31 corpus, that was 5 low-entropy rows and one ratified vector.
     *
     * @param cryptoProperties the raw properties block, which may be {@code null} for a component that carries none
     */
    public static MaterialRedaction of(JsonNode cryptoProperties) {
        ObjectNode payload = cryptoProperties == null || !cryptoProperties.isObject()
                ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                : cryptoProperties.deepCopy();
        JsonNode material = payload.get(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES);
        if (material != null && !material.isObject()) {
            // Dropped from BOTH payloads, not passed through. A producer emitting the block as an array or a string
            // put key material somewhere no redaction step can reach, so there is no envelope to put in its place --
            // which is what separates this from an uncontracted member. Retaining it would carry the plaintext into
            // the stored payload and into the backstop pre-image.
            payload.remove(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES);
            return new MaterialRedaction(payload, payload.deepCopy(), null, null, null, null,
                    List.of("non-object material block dropped"));
        }
        if (material == null) {
            return new MaterialRedaction(payload, payload.deepCopy(), null, null, null, null, List.of());
        }
        ObjectNode materialNode = (ObjectNode) material;
        JsonNode typeNode = materialNode.get("type");
        String materialType = typeNode != null && typeNode.isTextual() ? typeNode.textValue() : null;

        List<String> findings = new ArrayList<>();
        String identityDigest = null;
        String publishedDigest = null;
        Integer valueLength = null;

        if (materialNode.has(CbomNames.VALUE)) {
            JsonNode raw = materialNode.get(CbomNames.VALUE);
            if (raw == null || !raw.isTextual()) {
                // A non-string value cannot be hashed meaningfully and must not survive.
                materialNode.remove(CbomNames.VALUE);
                findings.add("non-string material value dropped");
            } else {
                String value = raw.textValue();
                // Code points, not UTF-16 units. The reference counts characters, so a material value carrying
                // anything outside the basic multilingual plane would otherwise be reported one length here and
                // another there -- and the length is served back in the stored payload.
                valueLength = value.codePointCount(0, value.length());
                // The identity digest exists for EVERY value, including low-entropy ones: the identity key is a hash
                // of the whole pre-image and is never exposed on any API, so using it costs nothing and keeps two
                // different passwords at one source coordinate apart. What is withheld for low-entropy material is
                // the digest in the STORED payload, which is served back and would be a reversible password hash.
                identityDigest = IdentityDigests.sha256Hex(value);
                publishedDigest = digestPublishable(materialType) ? identityDigest : null;
                if (publishedDigest == null) {
                    // The envelope carries no digest for any type, so this gate no longer decides what is stored --
                    // it decides what publishedDigest() may hand an internal caller. An unsalted SHA-256 of a
                    // password or a token is rainbow-table reversible, so a caller that put one on an API would leak
                    // it one step removed, and producers really do emit generic-password and jwt-token material.
                    findings.add("digest withheld: " + materialType + " is low-entropy material");
                }
                ObjectNode redacted = materialNode.objectNode();
                redacted.put("redacted", true);
                redacted.put("length", valueLength);
                materialNode.set(CbomNames.VALUE, redacted);
                if (materialType != null && SECRET_TYPES.contains(AsciiText.fold(AsciiText.strip(materialType)))) {
                    findings.add("producer inlined a value on material type " + materialType);
                }
            }
        }

        // The stored payload forks from the keyed one HERE, once the value carries an envelope rather than a
        // plaintext. Everything above is common to both; the member allowlist below is storage's alone.
        ObjectNode stored = payload.deepCopy();
        dropUncontractedMembers((ObjectNode) stored.get(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES), materialType,
                findings);
        return new MaterialRedaction(payload, stored, materialType, identityDigest, publishedDigest, valueLength,
                findings);
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
     * The members of {@code relatedCryptoMaterialProperties} this pipeline stores.
     *
     * <p>
     * Everything else is a producer extension and is dropped rather than enumerated, because {@code value} is not the
     * only member that can carry the plaintext -- or the plaintext's digest -- and the set of names that can is open: a
     * secret scanner fingerprints what it found so it can dedupe findings across runs, and that digest is exactly as
     * reversible as the one {@link #digestPublishable} refuses to publish.
     *
     * <p>
     * The first twelve are the 1.6 schema's members exactly, plus 1.7's {@code relatedCryptographicAssets} -- the
     * rename of {@code algorithmRef}, whose omission dropped the 1.7 reference array from storage while its 1.6
     * spelling survived. That is the parity hazard R2 exists to prevent, inverted onto storage.
     *
     * <p>
     * {@code relatedCryptoMaterialType} is <b>not</b> a schema member in either version and nothing in this pipeline
     * reads it: {@link #of} takes the type from {@code type} alone. It is kept because a producer spelling the material
     * type under the long name has stated a contracted fact rather than an extension, and dropping it lost that
     * statement from storage. It no longer has any effect on identity -- the keyed payload stopped depending on this
     * set -- so it is a storage-fidelity decision, and the set as a whole still has no ratified source. Both of those
     * are open questions on core#2165 item 9.
     */
    private static final Set<String> CONTRACTED_MEMBERS = Set
            .of("type", "relatedCryptoMaterialType", "id", "state", "algorithmRef", "relatedCryptographicAssets",
                    "creationDate", "activationDate", "updateDate", "expirationDate", "value", "size", "format",
                    "securedBy");

    /**
     * The one member kept only while the material's digest may be published.
     *
     * <p>
     * A producer fingerprint of high-entropy material is not reversible and is the discriminator the
     * {@code mat:fingerprint} tier keys on, so storage keeps it. On low-entropy material the same member is an unsalted
     * digest of a password, which is the thing {@link #digestPublishable} exists to withhold -- so there it goes, and
     * its absence is why the {@code mat:fingerprint} tier is unreachable for a low-entropy asset.
     */
    private static final Set<String> PUBLISHABLE_ONLY_MEMBERS = Set.of("fingerprint");

    /**
     * Drops every uncontracted member from the stored payload, and says which.
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
     * <b>Every type, not only the low-entropy ones.</b> Gating the allowlist on {@link #digestPublishable} ran the
     * protection opposite to the severity of the exposure. The value redaction keys on the single exact member name
     * {@code value}, so for exactly the types in {@link #SECRET_TYPES} an inlined plaintext under any other name was
     * stored verbatim with no finding: {@code {"type":"password","Value":"hunter2"}} was dropped and reported, while
     * {@code {"type":"private-key","pem":"-----BEGIN PRIVATE KEY-----…"}} was kept -- and this class's own doc calls
     * that case the one where a producer "has exfiltrated key material into a document the platform then aggregates
     * estate-wide". The same members {@code fingerprint} and {@code relatedCryptographicAssets} that the corpus
     * actually carries are allowed by name, so on real data the widening costs nothing and closes the plaintext hole.
     *
     * <p>
     * The drops are storage's alone: {@link #keyedPayload()} keeps every member, so nothing here can move an identity
     * key. What that buys is a plaintext under an uncontracted member reaching the identity pre-image -- never a stored
     * column, never a wire response, and never a log, since the pre-image has no production caller and the architecture
     * fence guards the accessor. It is the same exposure the value tier already accepts by hashing the plaintext, and
     * R2/R15 leave no room to strip more before a hash.
     *
     * <p>
     * The finding names every member removed, so nothing disappears without a record.
     */
    private static void dropUncontractedMembers(ObjectNode materialNode, String materialType, List<String> findings) {
        if (materialNode == null) {
            return;
        }
        boolean publishable = digestPublishable(materialType);
        List<String> dropped = new ArrayList<>();
        materialNode.fieldNames().forEachRemaining(name -> {
            boolean contracted = CONTRACTED_MEMBERS.contains(name)
                    || (publishable && PUBLISHABLE_ONLY_MEMBERS.contains(name));
            if (!contracted) {
                dropped.add(name);
            }
        });
        if (dropped.isEmpty()) {
            return;
        }
        dropped.sort(AsciiText.BY_CODE_POINT);
        dropped.forEach(materialNode::remove);
        findings
                .add("uncontracted members dropped from the stored payload, any of which may carry the plaintext or "
                        + "a reversible digest of it: " + String.join(", ", dropped));
    }

    /**
     * The redacted properties as identity reads them: R2/R15's projection, with the value under its envelope.
     *
     * <p>
     * Every member the producer stated is present, because R2 names the five reference fields as the whole of what a
     * hash may strip. This is not a payload to store, serve or log -- an uncontracted member may carry a plaintext the
     * storage allowlist removes -- and the architecture fence keeps the identity pre-image built from it off every
     * client-facing surface.
     */
    public ObjectNode keyedPayload() {
        return keyedPayload;
    }

    /** The redacted properties as storage reads them: contracted members only. This is what may be stored or served. */
    public ObjectNode storedPayload() {
        return storedPayload;
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
