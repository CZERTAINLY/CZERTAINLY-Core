package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
                inlinedSecretFinding(materialType, CbomNames.VALUE, findings);
            }
        }

        // The stored payload forks from the keyed one HERE, once the value carries an envelope rather than a
        // plaintext. Everything above is common to both; the member allowlist below is storage's alone.
        ObjectNode stored = payload.deepCopy();
        ObjectNode storedMaterial = (ObjectNode) stored.get(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES);
        // Read before the drop, because the severe finding needs the VALUE and not the member name. Raising it for
        // every dropped member reported a producer's benign metadata -- a number, a null, an object -- as confirmed
        // key-material exfiltration, which is a false positive on the loudest finding this class emits.
        List<String> inlined = storedMaterial == null
                ? List.of()
                : inlinedMemberNames(storedMaterial, digestPublishable(materialType));
        dropUncontractedMembers(storedMaterial, materialType, findings);
        projectRelatedAssets(storedMaterial, findings);
        inlined.forEach(member -> inlinedSecretFinding(materialType, member, findings));
        return new MaterialRedaction(payload, stored, materialType, identityDigest, publishedDigest, valueLength,
                findings);
    }

    /**
     * The uncontracted members carrying something that could be inlined material: a non-blank textual scalar.
     *
     * <p>
     * A predicate over the value rather than over the name, and deliberately a shape test rather than an entropy or
     * PEM-header test: the point is to separate "a producer put a string here" from "a producer put a flag, a count or
     * a nested object here", not to guess whether the string is a key. A digest of a secret is a string too, and is
     * exactly as worth reporting.
     *
     * <p>
     * <b>Uncontracted here means uncontracted for {@link #dropUncontractedMembers} too</b>, which is why the
     * publishability the drop computes is passed in rather than recomputed against {@link #CONTRACTED_MEMBERS} alone.
     * The two sets disagreeing reported a member the drop had <em>kept</em>: on the secret types that are not
     * low-entropy -- {@code private-key}, {@code secret-key}, {@code shared-secret}, {@code seed}, {@code key} -- a
     * textual {@code relatedCryptoMaterialType} survived storage and still raised the exfiltration finding, which is
     * the loudest thing this class emits, on a producer's benign metadata.
     */
    private static List<String> inlinedMemberNames(ObjectNode materialNode, boolean publishable) {
        List<String> carrying = new ArrayList<>();
        materialNode.properties().forEach(member -> {
            JsonNode value = member.getValue();
            boolean contracted = CONTRACTED_MEMBERS.contains(member.getKey())
                    || (publishable && PUBLISHABLE_ONLY_MEMBERS.contains(member.getKey()));
            if (!contracted && value.isTextual() && !AsciiText.isBlank(value.textValue())) {
                carrying.add(member.getKey());
            }
        });
        carrying.sort(AsciiText.BY_CODE_POINT);
        return carrying;
    }

    /**
     * Raises the exfiltration finding when a type that should never carry an inlined value carried one.
     *
     * <p>
     * Separate from the generic uncontracted-members finding and deliberately louder: this one names the type, so a
     * consumer can tell "a producer put a private key in a document" from "a producer sent a field we do not contract
     * for".
     */
    private static void inlinedSecretFinding(String materialType, String member, List<String> findings) {
        if (materialType != null && SECRET_TYPES.contains(AsciiText.fold(AsciiText.strip(materialType)))) {
            findings.add("producer inlined a value on material type " + materialType + " under member " + member);
        }
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
     * These are the 1.6 schema's members exactly, plus 1.7's {@code relatedCryptographicAssets} -- the rename of
     * {@code algorithmRef}, whose omission dropped the 1.7 reference array from storage while its 1.6 spelling
     * survived. That is the parity hazard R2 exists to prevent, inverted onto storage.
     *
     * <p>
     * {@code relatedCryptoMaterialType} was in this set and is not any more -- {@link #PUBLISHABLE_ONLY_MEMBERS} says
     * why. It was added as a stopgap for a layer error rather than as a statement about the contract: dropping it
     * re-keyed a ratified row, because the backstop pre-image ends in a projection digest over this payload and vector
     * {@code gen-068-mat-backstop} expects the member present. Splitting the payloads retired that reason, and a review
     * pass then showed the exemption was not merely unjustified but unsafe. What this set as a whole still lacks is a
     * ratified source, which is open on core#2165 item 9.
     */
    private static final String RELATED_CRYPTOGRAPHIC_ASSETS = "relatedCryptographicAssets";

    private static final Set<String> CONTRACTED_MEMBERS = Set
            .of("type", "id", "state", "algorithmRef", RELATED_CRYPTOGRAPHIC_ASSETS, "creationDate", "activationDate",
                    "updateDate", "expirationDate", "value", "size", "format", "securedBy");

    /**
     * The members each {@code relatedCryptographicAssets} entry keeps.
     *
     * <p>
     * {@link #CONTRACTED_MEMBERS} admits the array by its top-level name and {@link #dropUncontractedMembers} iterates
     * top-level names only, so every entry was preserved whole: a producer emitting
     * {@code [{"ref":"a1","digest":"<hash of the secret>"}]} kept that digest in the stored payload. The argument for
     * an allowlist rather than a denylist does not stop at depth one -- the set of member names able to carry a
     * secret's digest is open at every depth -- so the entries are projected onto this shape instead of being filtered
     * against a list of names to fear.
     *
     * <p>
     * 724 corpus entries carry {@code ref} (724 of them) and {@code type} (144) and nothing else, so the projection
     * costs 0 stored payloads today. Whether 1.7 contracts a third member here is open on core#2165 item 9 with the
     * rest of this set's ratified source; until it is answered a new member fails closed and is reported, which is the
     * direction this class takes everywhere else.
     */
    private static final Set<String> CONTRACTED_RELATED_ASSET_MEMBERS = Set.of("ref", "type");

    /**
     * The members kept only while the material's own digest may be published.
     *
     * <p>
     * {@code relatedCryptoMaterialType} is here rather than in {@link #CONTRACTED_MEMBERS} because it is an
     * <em>unrestricted</em> extension -- absent from both schemas, read by nothing, and able to hold whatever a
     * producer puts there, including a digest of the very value the withhold rule protects. Retaining it for every type
     * defeated that rule through the exemption meant to preserve fidelity: {@code type: "password"} carrying the
     * password's digest under the long spelling was stored and served. Here it survives where publishing such a digest
     * would already be safe and is dropped where it would not. The single corpus component carrying the member states
     * no type at all, which fails closed to low-entropy, so that one row is exactly the hazard case: 1 stored payload
     * changes, 0 keys.
     *
     * <p>
     * A producer fingerprint of high-entropy material is not reversible and is the discriminator the
     * {@code mat:fingerprint} tier keys on, so storage keeps it. On low-entropy material the same member is an unsalted
     * digest of a password, which is the thing {@link #digestPublishable} exists to withhold -- so storage drops it
     * there.
     *
     * <p>
     * <b>Dropping it no longer changes the tier.</b> While one payload served both purposes, a low-entropy asset's
     * fingerprint was gone before {@code material()} could read it, so the row reached {@code mat:backstop}; now the
     * keyed payload keeps it and the row keys on {@code mat:fingerprint} instead. That is a key move for that class,
     * and it is toward the reference: the specification's {@code MAT|<type>|F|...} carries no low-entropy exception and
     * the kernel keys the tier whatever the type. 0 corpus rows and 0 vectors -- all 453 corpus fingerprints sit on
     * publishable types.
     */
    private static final Set<String> PUBLISHABLE_ONLY_MEMBERS = Set.of("fingerprint", "relatedCryptoMaterialType");

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
     * key. What that buys is a retained plaintext reaching the identity pre-image, and how far it reaches is worth
     * being exact about. A plaintext under an <em>uncontracted</em> member enters only
     * {@code CanonicalJson.projectionDigest}, so it sits inside a SHA-256 and is never spelled into a slot. The
     * cleartext case is {@code fingerprint.content}, which the {@code mat:fingerprint} claim spells literally -- a tier
     * this split newly makes reachable for low-entropy material. Either way it reaches no stored column, no wire
     * response and no log: the pre-image has no production caller, {@link #keyedPayload()} is package-private, and the
     * architecture fence covers both spellings. It is the same exposure the value tier already accepts by hashing the
     * plaintext, and R2/R15 leave no room to strip more before a hash.
     *
     * <p>
     * The finding names every member removed, so nothing disappears without a record.
     */
    private static List<String> dropUncontractedMembers(ObjectNode materialNode, String materialType,
            List<String> findings) {
        if (materialNode == null) {
            return List.of();
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
            return List.of();
        }
        dropped.sort(AsciiText.BY_CODE_POINT);
        dropped.forEach(materialNode::remove);
        findings
                .add("uncontracted members dropped from the stored payload, any of which may carry the plaintext or "
                        + "a reversible digest of it: " + String.join(", ", dropped));
        return List.copyOf(dropped);
    }

    /**
     * Projects each {@code relatedCryptographicAssets} entry onto {@link #CONTRACTED_RELATED_ASSET_MEMBERS}.
     *
     * <p>
     * Storage's alone, like the drop above -- {@link #keyedPayload()} keeps the array whole, so nothing here can move
     * an identity key. An entry that is not an object states no reference and is removed rather than projected; an
     * entry whose contracted members are all absent stays as an empty object, because how many related assets the
     * producer stated is itself part of what the row records.
     */
    private static void projectRelatedAssets(ObjectNode materialNode, List<String> findings) {
        if (materialNode == null || !materialNode.has(RELATED_CRYPTOGRAPHIC_ASSETS)) {
            return;
        }
        JsonNode assets = materialNode.get(RELATED_CRYPTOGRAPHIC_ASSETS);
        if (!assets.isArray()) {
            materialNode.remove(RELATED_CRYPTOGRAPHIC_ASSETS);
            findings.add("non-array relatedCryptographicAssets dropped from the stored payload");
            return;
        }
        List<String> removed = new ArrayList<>();
        ArrayNode projected = materialNode.arrayNode();
        assets.forEach(entry -> {
            if (!entry.isObject()) {
                removed.add("<non-object entry>");
                return;
            }
            ObjectNode kept = projected.addObject();
            entry.properties().forEach(member -> {
                if (CONTRACTED_RELATED_ASSET_MEMBERS.contains(member.getKey()) && member.getValue().isTextual()) {
                    kept.set(member.getKey(), member.getValue());
                } else {
                    removed.add(member.getKey());
                }
            });
        });
        materialNode.set(RELATED_CRYPTOGRAPHIC_ASSETS, projected);
        if (!removed.isEmpty()) {
            removed.sort(AsciiText.BY_CODE_POINT);
            findings
                    .add("uncontracted members dropped from relatedCryptographicAssets entries, any of which may "
                            + "carry a reversible digest of the plaintext: " + String.join(", ", removed));
        }
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
    ObjectNode keyedPayload() {
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
