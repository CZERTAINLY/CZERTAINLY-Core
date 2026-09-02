package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The derivations that need a view of a whole document rather than of one component, computed once per document.
 *
 * <p>
 * Two of them refute a producer-supplied identifier that the document itself contradicts, and both exist because
 * <em>every</em> such identifier has been observed fabricated in real data. A certificate fingerprint claimed by two
 * certificates with different subjects, issuers and revocation states merges a revoked internal CA with an active
 * server certificate -- exactly the false negative the identity key exists to prevent. A cipher-suite code stamped on
 * three differently-named suites collapses five distinct suites onto one identity.
 *
 * <p>
 * Computing these per component is quadratic in document size: measured on the reference, the certificate pass alone
 * was half the run time and drove 286 050 distinguished-name normalizations. They are computed once here and passed
 * into the identity chain.
 *
 * <p>
 * <b>Scope is the caller's to widen.</b> Within one document this is enough, and it is what an ingest pass gets for
 * free. A digest two certificates claim in <em>different</em> documents needs a batch-scoped index, which cannot live
 * in a per-component pure function -- see {@link #refutedAcross}.
 *
 * <p>
 * <b>The document itself is not retained.</b> Only the derivations are: two sets of refuted identifiers and an index
 * from bom-ref to the component it names. Holding the parsed document would keep every inlined key material value alive
 * for as long as any asset of that document was being processed, reachable through an accessor and printable by
 * anything that interpolated the scope -- which is the redaction ordering defeated at the point of use rather than at
 * the point of redaction. The reference index does hold component nodes, which is unavoidable: resolving a reference
 * means reading its target. Those are the redacted-on-read path, and nothing hands the map out.
 */
public final class DocumentScope {

    /** Mirrors the extractor's bound, which mirrors the JSON parser's: nothing that parses is truncated. */
    private static final int MAX_DEPTH = 1000;

    private final Set<String> refutedCertificateDigests;
    private final Set<String> refutedSuiteCodes;
    private final Map<String, JsonNode> componentsByRef;
    private final Set<String> ambiguousRefs;

    private DocumentScope(Set<String> refutedCertificateDigests, Set<String> refutedSuiteCodes,
            Map<String, JsonNode> componentsByRef, Set<String> ambiguousRefs) {
        this.refutedCertificateDigests = refutedCertificateDigests;
        this.refutedSuiteCodes = refutedSuiteCodes;
        this.componentsByRef = componentsByRef;
        this.ambiguousRefs = ambiguousRefs;
    }

    /** An empty scope, for keying a component with no document around it. Every refutation set is empty. */
    public static DocumentScope none() {
        return new DocumentScope(Set.of(), Set.of(), Map.of(), Set.of());
    }

    /**
     * Indexes the document's components by {@code bom-ref}, treating a duplicated ref as naming nothing.
     *
     * <p>
     * <b>Ambiguity is unresolved, not first-one-wins.</b> {@code bom-ref} is producer-assigned and nothing in either
     * schema version makes it unique, and real producer output duplicates it: 6 corpus documents carry 27 duplicated
     * ref instances, one of them emitting {@code crypto/protocol/tls@TLSv1.3} three times in five documents.
     * First-in-document-order made a certificate's key depend on which serialization of one document the platform
     * happened to ingest -- permuting the components moved a real key from {@code 06b755a7…} to {@code eeba7e15…} --
     * and permutation-invariance is a property the extractor states, so document order cannot be allowed to decide
     * identity. An ambiguous ref therefore joins the absent, dangling and wrong-kind cases the resolution rule already
     * treats as unresolved.
     *
     * <p>
     * The counter-argument from the clean-room pass -- that an unresolved ref yields an empty slot, and empty-slot
     * certificates merge -- does not separate the two arms: both certificates carry the <em>same</em> duplicated ref
     * string, so first-one-wins hands them the same target and merges them too. What differs between the arms is only
     * whether document order can move a key. 0 corpus rows move either way, because no duplicated ref is currently
     * pointed at.
     *
     * <p>
     * Both arms owe the producer an ingest finding, and neither can raise one yet: {@link #ambiguousRefs()} carries
     * what a finding would name, and core#2073 owns the channel that reports it.
     */
    public static DocumentScope of(JsonNode document, AssetNormalizer normalizer) {
        if (document == null) {
            return none();
        }
        Map<String, JsonNode> byRef = new LinkedHashMap<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (JsonNode component : walk(document)) {
            JsonNode ref = component.get("bom-ref");
            if (ref != null && ref.isTextual() && byRef.putIfAbsent(ref.textValue(), component) != null) {
                duplicated.add(ref.textValue());
            }
        }
        duplicated.forEach(byRef::remove);
        return new DocumentScope(refute(certificateDigestClaims(document, normalizer)),
                refutedSuiteCodes(document, normalizer), byRef, Set.copyOf(duplicated));
    }

    /** True when the component states an {@code assetType} that is not the protocol type. */
    private static boolean statesANonProtocolType(JsonNode properties, AssetNormalizer normalizer) {
        JsonNode declared = properties == null ? null : properties.get("assetType");
        if (declared == null || !declared.isTextual()) {
            return false;
        }
        String routed = normalizer.normalizeAssetType(declared.textValue());
        return routed != null && !CbomNames.ASSET_TYPE_PROTOCOL.equals(routed);
    }

    /**
     * Every component in the document, including nested ones, depth-first in document order.
     *
     * <p>
     * Iterative rather than recursive, and bounded. {@code components} nests arbitrarily in both schema versions, so a
     * recursive walk over a hostile document nested a few thousand deep exhausts the stack and takes the whole ingest
     * with it. Real documents nest two or three deep.
     */
    public static List<JsonNode> walk(JsonNode document) {
        List<JsonNode> found = new ArrayList<>();
        Deque<int[]> depths = new ArrayDeque<>();
        Deque<JsonNode> pending = new ArrayDeque<>();
        pushChildren(document == null ? null : document.get("components"), 1, pending, depths);
        while (!pending.isEmpty()) {
            JsonNode component = pending.pop();
            int depth = depths.pop()[0];
            found.add(component);
            if (depth < MAX_DEPTH) {
                pushChildren(component.get("components"), depth + 1, pending, depths);
            }
        }
        return found;
    }

    private static void pushChildren(JsonNode components, int depth, Deque<JsonNode> pending, Deque<int[]> depths) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (int index = components.size() - 1; index >= 0; index--) {
            JsonNode child = components.get(index);
            if (child.isObject()) {
                pending.push(child);
                depths.push(new int[]{depth});
            }
        }
    }

    /**
     * Content digests this document contradicts itself about, which are unusable for every certificate claiming them.
     */
    public Set<String> refutedCertificateDigests() {
        return refutedCertificateDigests;
    }

    /** Cipher-suite codes this document stamps on differently-named suites. */
    public Set<String> refutedSuiteCodes() {
        return refutedSuiteCodes;
    }

    /** Resolves a document-internal bom-ref to its component, or {@code null} when it names none or more than one. */
    public JsonNode resolve(JsonNode ref) {
        return ref != null && ref.isTextual() ? componentsByRef.get(ref.textValue()) : null;
    }

    /**
     * Refs this document defines more than once, which resolve to nothing.
     *
     * <p>
     * Exposed for the ingest finding that both arms of the ambiguity rule require and that no channel in
     * {@code src/main} can yet report -- see {@link #of}. A caller that resolves nothing cannot otherwise tell an
     * ambiguous ref from an absent one, and the producer needs to hear which of the two it emitted.
     */
    public Set<String> ambiguousRefs() {
        return ambiguousRefs;
    }

    /**
     * The same refutation over a <em>batch</em> of documents rather than one.
     *
     * <p>
     * Document-scoped refutation is blind to the shape an estate actually produces: two certificates claiming one
     * placeholder digest in different documents still merge, which is an over-merge in the severe direction -- a
     * revoked CA can absorb an active server certificate, and the query "where is weak crypto deployed" then answers
     * CLEAN for a vulnerable host.
     *
     * <p>
     * This cannot be a per-component pure function: deciding it needs a view over every document in the batch, which is
     * why it belongs to ingest orchestration and is passed <em>into</em> identity rather than derived inside it. No
     * corpus can exercise the hazard -- 116 digests span more than one document, none spans more than one producer, and
     * none carries a cross-document contradiction -- so it is closed by construction and pinned by an authored
     * scenario, not by a corpus measurement.
     */
    public static Set<String> refutedAcross(Iterable<JsonNode> documents, AssetNormalizer normalizer) {
        Map<String, List<Map<String, String>>> claims = new LinkedHashMap<>();
        for (JsonNode document : documents) {
            certificateDigestClaims(document, normalizer)
                    .forEach((digest,
                            records) -> claims.computeIfAbsent(digest, key -> new ArrayList<>()).addAll(records));
        }
        return refute(claims);
    }

    /**
     * A digest two records contradict each other about is unusable for both.
     *
     * <p>
     * Refutation needs a genuine contradiction -- two records disagreeing on a field they <em>both</em> populate.
     * Comparing whole tuples made a sparse record (subject and issuer) conflict with a richer one describing the same
     * certificate, which destroyed a legitimate merge.
     */
    private static Set<String> refute(Map<String, List<Map<String, String>>> claims) {
        Set<String> refuted = new LinkedHashSet<>();
        claims.forEach((digest, records) -> {
            for (String field : List.of("subject", "issuer", "serial", "notBefore", "notAfter")) {
                Set<String> stated = new HashSet<>();
                records
                        .stream()
                        .filter(claim -> claim.containsKey(field))
                        .map(claim -> claim.get(field))
                        .forEach(stated::add);
                if (stated.size() > 1) {
                    refuted.add(digest);
                    return;
                }
            }
        });
        return Set.copyOf(refuted);
    }

    /** Every content digest claimed in this document, with the facts claimed beside it. */
    private static Map<String, List<Map<String, String>>> certificateDigestClaims(JsonNode document,
            AssetNormalizer normalizer) {
        Map<String, List<Map<String, String>>> claims = new LinkedHashMap<>();
        for (JsonNode component : walk(document)) {
            JsonNode properties = component.get("cryptoProperties");
            if (properties == null || !properties.isObject()) {
                continue;
            }
            // Normalized, not raw. The router normalizes the spelling, so a raw comparison meant an assetType of
            // "Certificate" was ROUTED as a certificate while staying invisible to refutation -- a safety control
            // evadable by capitalization.
            JsonNode assetType = properties.get("assetType");
            if (!CbomNames.ASSET_TYPE_CERTIFICATE
                    .equals(normalizer
                            .normalizeAssetType(
                                    assetType != null && assetType.isTextual() ? assetType.textValue() : null))) {
                continue;
            }
            JsonNode certificate = properties.get(CbomNames.CERTIFICATE_PROPERTIES);
            JsonNode certificateProperties = certificate != null && certificate.isObject() ? certificate : null;
            Map<String, String> facts = new LinkedHashMap<>();
            put(facts, "subject", DistinguishedNames
                    .normalize(text(certificateProperties, CbomNames.SUBJECT_NAME), normalizer.tables()));
            put(facts, "issuer", DistinguishedNames
                    .normalize(text(certificateProperties, CbomNames.ISSUER_NAME), normalizer.tables()));
            String serial = text(certificateProperties, "serialNumber");
            put(facts, "serial", AsciiText.fold(AsciiText.strip(serial == null ? "" : serial)));
            put(facts, "notBefore", ValidityTimestamps.normalize(text(certificateProperties, "notValidBefore")));
            put(facts, "notAfter", ValidityTimestamps.normalize(text(certificateProperties, "notValidAfter")));
            if (facts.isEmpty()) {
                continue;
            }
            for (String digest : CertificateDigests.claimed(component, certificateProperties)) {
                claims.computeIfAbsent(digest, key -> new ArrayList<>()).add(facts);
            }
        }
        return claims;
    }

    /**
     * Cipher-suite codes that one document stamps on differently-named suites, read only from components that are
     * protocols.
     *
     * <p>
     * One producer stamps the identical placeholder {@code ["0xC0","0x30"]} on TLS_AES_128_GCM_SHA256,
     * TLS_AES_256_GCM_SHA384 and TLS_CHACHA20_POLY1305_SHA256 alike, collapsing five distinct suites onto one identity.
     * So the code is corroborated against the suite name, and loses when it contradicts it.
     *
     * <p>
     * <b>Gated like the certificate pass, and for the same reason.</b> This walked every component and read any
     * {@code protocolProperties.cipherSuites} it found, while {@code certificateDigestClaims} two methods up gates on
     * the <em>normalized</em> {@code assetType} because a raw comparison made that control evadable by capitalization.
     * The asymmetry was reachable: an {@code algorithm} component carrying a stale protocol block could contribute a
     * second name for a real suite code and refute it document-wide, moving the identity of every genuine protocol row
     * that claims it. 0 of 107 corpus {@code cipherSuites} blocks sit on a non-protocol component, so nothing moves
     * today.
     *
     * <p>
     * <b>An absent {@code assetType} still contributes.</b> Skipping everything that does not normalize to
     * {@code protocol} would also skip a component that states no type at all -- which, for a block carrying
     * {@code cipherSuites}, is far more likely a protocol than not, and losing its refutation <em>over</em>-merges.
     * That is the opposite direction from the certificate pass, where losing a refutation under-merges, so the safe
     * gate here is "a type is stated and it is not protocol" rather than "the type is not protocol".
     */
    private static Set<String> refutedSuiteCodes(JsonNode document, AssetNormalizer normalizer) {
        Map<String, Set<String>> names = new LinkedHashMap<>();
        for (JsonNode component : walk(document)) {
            recordSuiteNames(contributedCipherSuites(component, normalizer), names);
        }
        Set<String> refuted = new LinkedHashSet<>();
        names.forEach((code, seen) -> {
            if (seen.size() > 1) {
                refuted.add(code);
            }
        });
        return Set.copyOf(refuted);
    }

    /**
     * The {@code cipherSuites} array a component is allowed to refute with, or null when it has none or is barred.
     */
    private static JsonNode contributedCipherSuites(JsonNode component, AssetNormalizer normalizer) {
        JsonNode properties = component.get("cryptoProperties");
        if (statesANonProtocolType(properties, normalizer)) {
            return null;
        }
        JsonNode protocol = properties == null ? null : properties.get("protocolProperties");
        JsonNode suites = protocol == null ? null : protocol.get("cipherSuites");
        return suites != null && suites.isArray() ? suites : null;
    }

    /**
     * Files every named suite under its code. A code seen under two names is refuted; one name repeated is not, so the
     * value is a set rather than a count.
     */
    private static void recordSuiteNames(JsonNode suites, Map<String, Set<String>> names) {
        if (suites == null) {
            return;
        }
        for (JsonNode suite : suites) {
            if (!suite.isObject()) {
                continue;
            }
            String code = CipherSuites.code(suite.get("identifiers"));
            JsonNode name = suite.get("name");
            if (code != null && name != null && name.isTextual() && !AsciiText.isBlank(name.textValue())) {
                names
                        .computeIfAbsent(code, key -> new HashSet<>())
                        .add(AsciiText.upper(AsciiText.strip(name.textValue())));
            }
        }
    }

    private static void put(Map<String, String> facts, String field, String value) {
        if (value != null && !value.isEmpty()) {
            facts.put(field, value);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
