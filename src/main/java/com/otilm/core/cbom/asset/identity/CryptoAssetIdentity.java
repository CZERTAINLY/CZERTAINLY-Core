package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ratified identity chain: routes a component to its tier, builds that tier's pre-image, and hashes it.
 *
 * <p>
 * <b>Routing is on {@code cryptoProperties.assetType} alone</b>, never on which properties block happens to be present.
 * A producer bug once stamped {@code relatedCryptoMaterialProperties} onto algorithms and certificates; a
 * presence-based router would have pulled those into the wrong chain and minted phantom material rows from the empty
 * blocks it left behind.
 *
 * <p>
 * The identity value is SHA-256 over the pre-image encoded UTF-8, rendered lowercase hex. Slots join with {@code |} and
 * are percent-escaped; field order within a tier is fixed. <b>Escaping applies to outer slots only</b> -- a value that
 * goes into a string which is then hashed, with only the digest reaching a slot, is left literal.
 *
 * <p>
 * This exists as bytes rather than as groupings because a partition-based suite cannot check a hash. One
 * proof-of-concept round passed 62 of 62 scenarios while carrying 66 unresolved guesses: two conformant implementations
 * could write different keys for one asset and never be able to share a table.
 */
public record CryptoAssetIdentity(AssetNormalizer normalizer) {

    /** The tier vocabulary's version marker, carried inside certificate pre-images. */
    private static final String SPEC_ID = "v1";

    /**
     * The version of the KEYING RULES, stamped on every row.
     *
     * <p>
     * Deliberately not part of any pre-image: folding it in would re-key every row on a bump, re-migrating the whole
     * inventory, whereas recording it makes staleness a query. It is bumped whenever a ruling changes a key -- and this
     * generation is the first to route by tier rather than to frame ten typed fields, so every key this build writes
     * differs from the previous generation's.
     *
     * <p>
     * Note what the stamp can and cannot buy. A row keyed on a certificate's distinguished-name composite cannot be
     * re-keyed from the stored columns, because the composite's inputs -- subject, issuer, validity, public key -- are
     * not columns. So a stale row is <em>findable</em> here but not recomputable: repairing it means re-ingesting its
     * source document, which is the sync path's job, not a sweep over the asset table.
     */
    public static final int RULESET_VERSION = 2;

    private static final Pattern CLAIM_PROPERTY = Pattern
            .compile("lifecycle|observation|assurance|deployment", Pattern.CASE_INSENSITIVE);

    /** The token a claimed asset carries into its key, and the value the claim detector reports. */
    private static final String CLAIMED = "claimed";

    private static final Set<String> CLAIM_VALUES = Set
            .of(CLAIMED, "declared", "capability", "supported", "supports", "capable");

    /** Posture tokens a protocol name may carry. Deliberately closed: a free-text discriminator over-splits. */
    private static final List<String> POSTURE_TOKENS = List.of("cnsa", "pqc", "fips", "suite-b", "suiteb");

    private static final Pattern ENDPOINT_PORT = Pattern.compile("[:/](\\d{2,5})(?![0-9A-Za-z])");

    private static final Pattern PROTOCOL_PREFIX = Pattern
            .compile("^(?:DTLS|TLS|SSL|SSH)\\s*v?", Pattern.CASE_INSENSITIVE);

    private static final Pattern TWO_DIGITS = Pattern.compile("\\d{2}");

    private static final Pattern ALL_DIGITS = Pattern.compile("\\d+");

    private static final Pattern DOUBLE_SPACE = Pattern.compile("  +");

    /** The pipeline this chain keys over. Exposed so a caller builds document scope from the same tables. */
    @Override
    public AssetNormalizer normalizer() {
        return normalizer;
    }

    /** The identity of one component, the chain step that produced it, and everything the pipeline derived. */
    public record Identity(String key, String preImage, String step, NormalizedAsset asset,
            MaterialRedaction redaction) {
    }

    /** Keys a component with no document around it: no reference resolves and nothing is refuted. */
    public Identity of(JsonNode component) {
        return of(component, DocumentScope.none(), Set.of());
    }

    /**
     * Keys a component within its document.
     *
     * @param batchRefutedDigests digests a batch-scoped index found contradicted across documents; empty reduces to
     * document-scoped behaviour
     */
    public Identity of(JsonNode component, DocumentScope scope, Set<String> batchRefutedDigests) {
        AssetNormalizer.Result normalized = normalizer.normalize(component);
        NormalizedAsset asset = normalized.asset();
        JsonNode properties = normalized.redaction().payload();

        String preImage;
        String step;
        switch (asset.assetType() == null ? "" : asset.assetType()) {
            case CbomNames.ASSET_TYPE_ALGORITHM -> {
                String[] built = algorithm(asset, properties);
                preImage = built[0];
                step = built[1];
            }
            case CbomNames.ASSET_TYPE_CERTIFICATE -> {
                String[] built = certificate(component, properties, scope, batchRefutedDigests);
                preImage = built[0];
                step = built[1];
            }
            case CbomNames.ASSET_TYPE_PROTOCOL -> {
                String[] built = protocol(component, properties, scope);
                preImage = built[0];
                step = built[1];
            }
            case CbomNames.ASSET_TYPE_RELATED_CRYPTO_MATERIAL -> {
                String[] built = material(component, properties, normalized.redaction());
                preImage = built[0];
                step = built[1];
            }
            default -> {
                preImage = backstop(asset, properties);
                step = "backstop:unknown-type";
            }
        }

        // Applied uniformly to every tier rather than added to each tuple: whether an asset is a claim or an
        // observation is orthogonal to which chain step answered, and one place is one place to review. Appended only
        // for a claim, so an observation keys exactly as it did before this slot existed.
        if (observationMode(component) != null) {
            preImage = preImage + "|" + KeySlot.of(CLAIMED);
            asset
                    .note("D0: this asset is a stated CAPABILITY, not an observed deployment, so it keys separately from "
                            + "a scan of the same algorithm");
        }

        recordCaseRisk(component, properties, asset, step);
        return new Identity(Digests.sha256Hex(preImage), preImage, step, asset, normalized.redaction());
    }

    /**
     * The unroutable tier: a component typed cryptographic-asset with an assetType this specification does not know, or
     * with no {@code cryptoProperties} object at all.
     *
     * <p>
     * Such a component is <b>not skipped</b>. The name is part of the key because without it the projection digest of
     * an absent properties object is the SAME for every such component, so every broken asset in the estate collapses
     * into one row whose elected payload is whichever arrived first. That is an over-merge, the direction the
     * prefer-a-visible-split rule exists to forbid.
     */
    private String backstop(NormalizedAsset asset, JsonNode properties) {
        return "RAW|" + KeySlot.of(asset.assetType()) + "|" + CanonicalJson.projectionDigest(properties) + "|"
                + KeySlot.of(AsciiText.fold(collapse(asset.name())));
    }

    private String[] algorithm(NormalizedAsset asset, JsonNode properties) {
        if (asset.family() != null) {
            // `primitive` is NOT in the key. Carrying it produced 434 keys where 399 are correct, and 426 assets sat
            // in groups that disagreed with themselves about it: producers describing one RSA-2048 emit
            // {signature, pke, kem}. It stays a stored, indexed, filterable column.
            return new String[]{
                    "ALG|" + join(asset.family(), text(asset.parameterSet()), asset.curve(), asset.mode(),
                            asset.padding(), asset.variant()),
                    "alg:family"};
        }
        if (asset.name() != null && !AsciiText.isBlank(asset.name())) {
            // No family derivable, so the normalized name becomes the discriminator. Without it one producer's
            // PRIVATE KEY, RAW and MGF1 collapse into one meaningless family-less row.
            //
            // The token is NOT pre-escaped: the join below escapes every slot, so escaping here produced
            // `private%2520key` -- a percent sign escaped twice. No implementer reading the specification would
            // reproduce that.
            String token = AsciiText.fold(collapse(AsciiText.strip(asset.name())));
            return new String[]{
                    "ALG||" + join(token, text(asset.parameterSet()), asset.curve(), asset.mode(), asset.padding(),
                            asset.variant()),
                    "alg:name"};
        }
        return new String[]{"RAW|algorithm|" + CanonicalJson.projectionDigest(properties), "alg:backstop"};
    }

    /**
     * Content hash first, distinguished-name composite last.
     *
     * <p>
     * This inverts the original design, which engineered the composite as primary on the belief that nobody emits
     * component hashes. Measured, {@code component.hashes[]} is present on 6 of 6 real certificate instances and the
     * 1.7-only {@code fingerprint} on 0 of 6, so the content hash is the primary path. Promoting a collision-prone,
     * normalization-dependent key over a content hash present in 100% of the data would be a strict downgrade.
     */
    private String[] certificate(JsonNode component, JsonNode properties, DocumentScope scope,
            Set<String> batchRefutedDigests) {
        JsonNode certificate = objectOrNull(properties.get(CbomNames.CERTIFICATE_PROPERTIES));
        Set<String> refuted = new TreeSet<>(scope.refutedCertificateDigests());
        refuted.addAll(batchRefutedDigests);

        // Each claimed digest is tried in turn. A refuted one must not suppress a sound one: a producer stamping the
        // same placeholder fingerprint on two certificates may still compute correct, distinct component hashes for
        // both, and falling straight through to the composite would strand them.
        List<String> claimed = CertificateDigests.claimed(component, certificate);
        for (int index = 0; index < claimed.size(); index++) {
            String digest = claimed.get(index);
            if (refuted.contains(digest)) {
                continue;
            }
            boolean fromFingerprint = index == 0 && certificate != null && certificate.get("fingerprint") != null;
            return new String[]{
                    "CRT|H|" + SPEC_ID + "|" + KeySlot.of(digest),
                    fromFingerprint ? "crt:fingerprint" : "crt:component-hash"};
        }

        String serial = text(certificate, "serialNumber");
        String issuer = DistinguishedNames.normalize(text(certificate, CbomNames.ISSUER_NAME), normalizer.tables());
        if (serial != null && !AsciiText.isBlank(serial) && issuer != null) {
            return new String[]{
                    "CRT|S|" + SPEC_ID + "|" + KeySlot.of(AsciiText.fold(AsciiText.strip(serial))) + "|"
                            + KeySlot.of(issuer),
                    "crt:serial+issuer"};
        }

        String subject = DistinguishedNames.normalize(text(certificate, CbomNames.SUBJECT_NAME), normalizer.tables());
        if (subject != null && issuer != null) {
            return new String[]{
                    "CRT|D|" + SPEC_ID + "|" + Digests.sha256Hex(dnPreImage(properties, scope)),
                    "crt:dn-composite"};
        }

        String token = ComponentNames.stableToken(text(component, "name"));
        if (subject != null) {
            // Reached when the composite is not constructible -- typically a CN-only subject with no issuer. It gets
            // its own row and is never merged into a full-DN row: two internal CAs both issue CN=localhost, and
            // merging them would make "where is weak crypto deployed" answer CLEAN for a vulnerable host.
            String discriminator = Occurrences.discriminator(component);
            // The occurrence discriminator is appended ONLY when the component has occurrences. Emitting a trailing
            // empty slot re-keys every degenerate certificate.
            String suffix = discriminator == null ? "" : "|" + discriminator;
            String validity = Timestamps.normalize(text(certificate, "notValidBefore")) + "|"
                    + Timestamps.normalize(text(certificate, "notValidAfter"));
            String step = DistinguishedNames.isCommonNameOnly(subject) ? "crt:cn-only" : "crt:subject-only";
            return new String[]{
                    "CRT|C|" + SPEC_ID + "|" + KeySlot.of(subject) + "|" + validity + "|" + KeySlot.of(token) + suffix,
                    step};
        }
        // The backstop needs the name too: two certificates with no digest, no issuer and no subject --
        // `server-rsa-2048.pem` and `server-ecdsa-p256.pem` in a real document -- merged on a payload hash alone.
        return new String[]{
                "RAW|certificate|" + KeySlot.of(token) + "|" + CanonicalJson.projectionDigest(properties),
                "crt:backstop"};
    }

    /**
     * The five-field string the distinguished-name composite hashes, in order.
     *
     * <p>
     * Published as an operation because the key carries only its digest. One proof-of-concept round could not reproduce
     * this step and tried 768 combinations of spelling, timestamp form and public-key candidate before giving up. A
     * hashed slot must publish its input or it is not testable.
     *
     * <p>
     * Note the values here are <b>not</b> slot-escaped: only the SHA-256 of this string reaches a slot, so the inner
     * pre-image carries a space intact where an outer slot would carry {@code %20}. Getting that backwards re-keys
     * every certificate.
     */
    public String dnPreImage(JsonNode properties, DocumentScope scope) {
        JsonNode certificate = objectOrNull(properties.get(CbomNames.CERTIFICATE_PROPERTIES));
        return String
                .join("|",
                        nullToEmpty(DistinguishedNames
                                .normalize(text(certificate, CbomNames.SUBJECT_NAME), normalizer.tables())),
                        nullToEmpty(DistinguishedNames
                                .normalize(text(certificate, CbomNames.ISSUER_NAME), normalizer.tables())),
                        Timestamps.normalize(text(certificate, "notValidBefore")),
                        Timestamps.normalize(text(certificate, "notValidAfter")),
                        KeySlot.of(publicKeyDigest(properties, scope)));
    }

    /**
     * Identifies the certificate's public key by CONTENT, never by its bom-ref.
     *
     * <p>
     * A bom-ref is producer-assigned and document-scoped: the same certificate's {@code subjectPublicKeyRef} pointed at
     * an <em>algorithm</em> component on one branch of a producer and at the <em>public key</em> component on another,
     * so hashing the ref string would make identity depend on which build emitted the document.
     *
     * <p>
     * Two tiers, because refusing an algorithm target outright produced a wrong merge in real data: an nginx example
     * carries two self-signed certificates with identical subject, issuer and validity whose only difference is that
     * one points at an RSA-2048 algorithm and the other at an ECDSA-P256 one. Returning nothing for both collapsed two
     * genuinely different certificates into one row.
     */
    private String publicKeyDigest(JsonNode properties, DocumentScope scope) {
        JsonNode certificate = objectOrNull(properties.get(CbomNames.CERTIFICATE_PROPERTIES));
        JsonNode ref = certificate == null ? null : certificate.get("subjectPublicKeyRef");
        JsonNode related = certificate == null ? null : certificate.get("relatedCryptographicAssets");
        if (related != null && related.isArray()) {
            for (JsonNode entry : related) {
                JsonNode type = entry.isObject() ? entry.get("type") : null;
                if (type != null && type.isTextual()
                        && ("publicKey".equals(type.textValue()) || "public-key".equals(type.textValue()))) {
                    ref = entry.get("ref");
                    break;
                }
            }
        }
        JsonNode target = scope.resolve(ref);
        if (target == null) {
            return null;
        }
        JsonNode targetProperties = objectOrNull(target.get("cryptoProperties"));
        JsonNode material = targetProperties == null
                ? null
                : objectOrNull(targetProperties.get(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES));
        JsonNode value = material == null ? null : material.get("value");
        if (value != null && value.isObject() && value.get("sha256") != null) {
            return "K:" + value.get("sha256").asText();
        }
        if (value != null && value.isTextual() && !AsciiText.isBlank(value.textValue())) {
            return "K:" + MaterialValueDigest.of(value.textValue());
        }
        // No key material on the target. Identify it by its own normalized identity so the composite still
        // discriminates, rather than degrading to an empty slot.
        JsonNode targetType = targetProperties == null ? null : targetProperties.get("assetType");
        if (targetType != null && targetType.isTextual() && "algorithm".equals(targetType.textValue())) {
            AssetNormalizer.Result targetNormalized = normalizer.normalize(target);
            return "A:"
                    + Digests.sha256Hex(algorithm(targetNormalized.asset(), targetNormalized.redaction().payload())[0]);
        }
        return null;
    }

    private String[] protocol(JsonNode component, JsonNode properties, DocumentScope scope) {
        JsonNode protocol = objectOrNull(properties.get("protocolProperties"));
        String rawKind = text(protocol, "type");
        String kind = rawKind == null || AsciiText.isBlank(rawKind) ? null : AsciiText.fold(AsciiText.strip(rawKind));
        String version = normalizeProtocolVersion(text(protocol, "version"));
        String suites = CipherSuites.digest(properties, scope.refutedSuiteCodes());
        String configuration = protocolConfiguration(text(component, "name"));
        // Appended only when it carries something. An empty slot that still contributes a separator would re-key
        // every protocol row in the estate for no change in meaning -- 120 of 145 corpus rows.
        String suffix = configuration == null ? "" : "|" + KeySlot.of(configuration);

        if (kind != null && version != null && suites != null) {
            return new String[]{
                    "PRT|" + KeySlot.of(kind) + "|" + KeySlot.of(version) + "|" + suites + suffix,
                    "prt:type+version+suites"};
        }
        // Tier 2 is for a row that offered NO suites. A row that declared suites which could not be resolved falls to
        // the name tier instead, so "we know it has suites and cannot read them" never merges with "nothing was said".
        // Without the guard, tier 2 fired for every versioned row and the name tier below was unreachable.
        if (kind != null && version != null && !CipherSuites.declared(properties)) {
            return new String[]{"PRT|" + KeySlot.of(kind) + "|" + KeySlot.of(version) + suffix, "prt:type+version"};
        }
        if (kind != null) {
            // Below the suite digest the NAME carries the discriminating information. Three different TLS suites
            // modelled as protocol assets collapsed onto ONE identity because they share a type, carry no version and
            // no cipherSuites array, and sit at the same occurrence.
            String token = ComponentNames.stableToken(text(component, "name"));
            String discriminator = Occurrences.discriminator(component);
            if (version != null && !token.isEmpty()) {
                return new String[]{
                        "PRT|" + KeySlot.of(kind) + "|" + KeySlot.of(version) + "|N:" + KeySlot.of(token),
                        "prt:type+version+name"};
            }
            if (discriminator != null) {
                return new String[]{
                        "PRT|" + KeySlot.of(kind) + "||" + KeySlot.of(token) + "|" + discriminator,
                        "prt:type+occurrence"};
            }
            if (!token.isEmpty()) {
                return new String[]{"PRT|" + KeySlot.of(kind) + "||" + KeySlot.of(token), "prt:type+name"};
            }
            // Terminal fall-through. The version slot is carried and emitted even when empty, which makes the shape
            // regular against tier 1 -- it used to be dropped, so TLS 1.2, TLS 1.3 and a version-less row all shared
            // one identity in the nameless case. It also carries the suites-declared marker, without which the
            // version-less case still merged the two states tier 2 exists to separate. `declared` is a closed token
            // and cannot collide with the digest this slot otherwise holds, because a digest is hex.
            String declared = CipherSuites.declared(properties) ? "declared" : "";
            return new String[]{
                    "PRT|" + KeySlot.of(kind) + "|" + (version == null ? "" : KeySlot.of(version)) + "|" + declared
                            + suffix,
                    "prt:type-only"};
        }
        // The name is in the key, mirroring the unknown-type backstop. Two type-less protocol components with
        // byte-identical properties and different names used to merge. Stated precisely: the same name at different
        // occurrence locations still merges here, exactly as it does on the tier this mirrors.
        String rawName = text(component, "name");
        return new String[]{
                "RAW|protocol|" + CanonicalJson.projectionDigest(properties) + "|"
                        + KeySlot.of(AsciiText.fold(collapse(AsciiText.strip(rawName == null ? "" : rawName)))),
                "prt:backstop"};
    }

    /**
     * {@code TLSv1.3}, {@code TLS 1.3} and {@code 1.3} all become {@code 1.3}; sentinels become absent.
     *
     * <p>
     * A producer can emit {@code version: "n/a"} -- its parser returns that string on unknown input and the caller does
     * not guard -- which would otherwise grow a permanent {@code PRT|tls|n/a} bucket alongside the real one.
     */
    public String normalizeProtocolVersion(String raw) {
        if (raw == null || normalizer.tables().isSentinel(raw)) {
            return null;
        }
        String stripped = AsciiText.strip(raw);
        String text = AsciiText.strip(PROTOCOL_PREFIX.matcher(stripped).replaceFirst(""));
        if (!text.equals(stripped) && TWO_DIGITS.matcher(text).matches()) {
            // The compressed spelling: `TLS12` is TLS 1.2, not version 12. Reading it as a whole number stored a
            // nonsense version AND split the asset from the `1.2` bucket every other spelling lands in. Scoped to a
            // name that actually carried a protocol prefix, so a bare `12` is still whatever the producer meant.
            return text.charAt(0) + "." + text.charAt(1);
        }
        if (ALL_DIGITS.matcher(text).matches()) {
            return text + ".0";
        }
        return AsciiText.isDottedDigits(text, 1) ? text : null;
    }

    /**
     * The identity-bearing part of a protocol component's name: posture, then port.
     *
     * <p>
     * Both discriminators are deliberately closed rather than free text, because a free-text residue over-splits. A
     * label outside these two shapes still merges, which is what keeps {@code TLS 1} beside {@code TLSv1.0} and
     * {@code JDK TLS 1.2} beside {@code TLSv1.2} -- while keeping a posture claim ({@code (CNSA 2.0)} against
     * {@code (PQC)}) and an endpoint (three ports collapsed into one row) apart.
     */
    public String protocolConfiguration(String name) {
        if (name == null || AsciiText.isBlank(name)) {
            return null;
        }
        String folded = AsciiText.fold(name);
        TreeSet<String> parts = new TreeSet<>();
        POSTURE_TOKENS.stream().filter(folded::contains).forEach(parts::add);
        TreeSet<String> ports = new TreeSet<>();
        Matcher matcher = ENDPOINT_PORT.matcher(folded);
        while (matcher.find()) {
            ports.add(matcher.group(1));
        }
        List<String> ordered = new ArrayList<>(parts);
        ordered.addAll(ports);
        return ordered.isEmpty() ? null : String.join(",", ordered);
    }

    /**
     * Identity for key material. In practice the fallback IS the identity function.
     *
     * <p>
     * Measured: the primary chain -- id, fingerprint, value digest -- fires on 8 of 51 real material assets. One
     * producer emits {@code {"type": "secret-key"}} and nothing else for all 37 of its assets, so 84% reach the
     * occurrence tier.
     */
    private String[] material(JsonNode component, JsonNode properties, MaterialRedaction redaction) {
        JsonNode material = objectOrNull(properties.get(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES));
        String rawKind = text(material, "type");
        String kind = AsciiText.fold(AsciiText.strip(rawKind == null ? "" : rawKind));

        // Content-derived tiers first. `id` is a PRODUCER LABEL, not a global identifier: real corpora carry both a
        // UUID stable across documents and hand-written labels like `server-key-2024`, and nothing distinguishes the
        // two. Two different keys can share an id, so it must never outrank a fingerprint or a value digest.
        JsonNode fingerprint = material == null ? null : material.get("fingerprint");
        if (fingerprint != null && fingerprint.isObject() && fingerprint.get(CbomNames.CONTENT) != null
                && !fingerprint.get(CbomNames.CONTENT).isNull()) {
            JsonNode algorithm = fingerprint.get("alg");
            String label = AsciiText.fold(algorithm == null || algorithm.isNull() ? "unknown" : algorithm.asText());
            return new String[]{
                    "MAT|" + KeySlot.of(kind) + "|F|" + KeySlot.of(label) + ":"
                            + KeySlot.of(AsciiText.fold(fingerprint.get(CbomNames.CONTENT).asText())),
                    "mat:fingerprint"};
        }
        if (redaction.identityDigest() != null) {
            return new String[]{"MAT|" + KeySlot.of(kind) + "|V|" + redaction.identityDigest(), "mat:value-hash"};
        }
        String identifier = text(material, "id");
        if (identifier != null && !AsciiText.isBlank(identifier)) {
            return new String[]{"MAT|" + KeySlot.of(kind) + "|I|" + KeySlot.of(AsciiText.strip(identifier)), "mat:id"};
        }

        // Below the content-derived tiers the type slot may carry nothing at all -- `other` is a catch-all -- so the
        // stable part of the name joins the discriminator. That is what keeps a CRL and a CSR apart.
        String token = ComponentNames.stableToken(text(component, "name"));
        String discriminator = Occurrences.discriminator(component);
        if (discriminator != null) {
            return new String[]{
                    "MAT|" + KeySlot.of(kind) + "|O|" + KeySlot.of(token) + "|" + discriminator,
                    "mat:occurrence"};
        }
        return new String[]{
                "MAT|" + KeySlot.of(kind) + "|P|" + KeySlot.of(token) + "|"
                        + CanonicalJson.projectionDigest(properties),
                "mat:backstop"};
    }

    /**
     * {@code "claimed"} when a document marks this asset as a capability rather than an observed deployment.
     *
     * <p>
     * A vendor's "this product supports post-quantum crypto" and an operator's scan of a box that is not running it are
     * different facts about different things, and the merge rule -- richest description wins -- let the brochure
     * overwrite the scan. CycloneDX has no native field for this, so the signal comes from a marker property, and until
     * a producer emits one every asset is an observation and every key is unchanged.
     */
    public String observationMode(JsonNode component) {
        JsonNode properties = component == null ? null : component.get("properties");
        if (properties == null || !properties.isArray()) {
            return null;
        }
        for (JsonNode entry : properties) {
            if (!entry.isObject()) {
                continue;
            }
            JsonNode name = entry.get("name");
            JsonNode value = entry.get("value");
            if (name != null && name.isTextual() && CLAIM_PROPERTY.matcher(name.textValue()).find() && value != null
                    && value.isTextual() && CLAIM_VALUES.contains(AsciiText.fold(AsciiText.strip(value.textValue())))) {
                return CLAIMED;
            }
        }
        return null;
    }

    /**
     * Records that this row carries a value the ASCII fold left unfolded, but only where such a value actually reaches
     * the key for the tier that fired.
     *
     * <p>
     * This is provenance, not a claim that a duplicate exists: whether one does is a question about the estate, which
     * only a batch-scoped detector can answer.
     */
    private void recordCaseRisk(JsonNode component, JsonNode properties, NormalizedAsset asset, String step) {
        List<String> keyed = new ArrayList<>();
        if (step.startsWith("crt:")) {
            JsonNode certificate = objectOrNull(properties.get(CbomNames.CERTIFICATE_PROPERTIES));
            keyed.add(text(certificate, CbomNames.SUBJECT_NAME));
            keyed.add(text(certificate, CbomNames.ISSUER_NAME));
        }
        if ("mat:id".equals(step)) {
            keyed.add(text(objectOrNull(properties.get(CbomNames.RELATED_CRYPTO_MATERIAL_PROPERTIES)), "id"));
        }
        if (Set
                .of("alg:name", "crt:cn-only", "crt:subject-only", "crt:backstop", "mat:occurrence", "mat:backstop",
                        "prt:type+name", "prt:type+occurrence", "prt:type+version+name")
                .contains(step)) {
            keyed.add(text(component, "name"));
        }
        if (step.contains("occurrence")) {
            JsonNode evidence = component.get("evidence");
            JsonNode occurrences = evidence == null ? null : evidence.get("occurrences");
            if (occurrences != null && occurrences.isArray()) {
                occurrences.forEach(occurrence -> {
                    if (occurrence.isObject()) {
                        keyed.add(text(occurrence, "location"));
                    }
                });
            }
        }
        List<String> present = keyed.stream().filter(java.util.Objects::nonNull).toList();
        asset.setKeyedCaseValues(present);
        asset.setAsciiCaseRisk(unfoldedCaseRisk(present));
        if (!asset.asciiCaseRisk().isEmpty()) {
            // Worded to avoid naming the key: this note is stored in the row's provenance block and can be served,
            // and the exposure fence refuses a production source outside persistence that names the key at all.
            asset
                    .note("R12: non-ASCII cased characters " + String.join("", asset.asciiCaseRisk())
                            + " are keyed unfolded; the case-fold twin detector examines this row");
        }
    }

    /**
     * Non-ASCII cased characters found in values that reach the identity key.
     *
     * <p>
     * Their presence means this asset may hold a duplicate row that a Unicode-folding implementation would have merged
     * -- a deliberate trade, since a stable duplicate beats an unstable merge, but one an operator must be able to SEE
     * rather than discover as an unexplained near-duplicate.
     */
    public static List<String> unfoldedCaseRisk(List<String> values) {
        TreeSet<String> found = new TreeSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            value
                    .codePoints()
                    .filter(codePoint -> codePoint > 127)
                    .filter(codePoint -> Character.toLowerCase(codePoint) != Character.toUpperCase(codePoint))
                    .forEach(codePoint -> found.add(new String(Character.toChars(codePoint))));
        }
        return List.copyOf(found);
    }

    private static String join(String... slots) {
        StringBuilder joined = new StringBuilder();
        for (int index = 0; index < slots.length; index++) {
            if (index > 0) {
                joined.append('|');
            }
            joined.append(KeySlot.of(slots[index]));
        }
        return joined.toString();
    }

    private static String collapse(String value) {
        return value == null ? "" : DOUBLE_SPACE.matcher(value).replaceAll(" ");
    }

    private static String text(Integer value) {
        return value == null ? null : value.toString();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static JsonNode objectOrNull(JsonNode node) {
        return node != null && node.isObject() ? node : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
