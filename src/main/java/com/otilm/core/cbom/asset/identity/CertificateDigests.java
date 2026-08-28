package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The content digests a certificate claims, in preference order, reduced to one spelling. */
public final class CertificateDigests {

    /**
     * Strongest first. SHA-1 is last but still accepted: a weak digest still identifies, and refusing it loses a row.
     */
    private static final List<String> PREFERENCE = List.of("SHA-256", "SHA-384", "SHA-512", "SHA-1");

    private CertificateDigests() {
    }

    /**
     * Every digest this certificate claims, the 1.7 {@code fingerprint} field first and {@code component.hashes[]}
     * second.
     *
     * <p>
     * They collapse into <b>one</b> content-digest tier rather than two. Tagging the 1.7 field differently from an
     * identical {@code component.hashes[]} digest would fork the same certificate between a 1.6 and a 1.7 producer on
     * the strength of <em>where</em> the same bytes were written.
     */
    /**
     * The 1.7 fingerprint field's digest, or {@code null} when the field is absent or carries no content.
     *
     * <p>
     * Named rather than inlined because the chain step is derived from it: labelling a key {@code crt:fingerprint} on
     * the mere presence of a {@code fingerprint} node reported that step for a key actually built from
     * {@code component.hashes[]}, whenever the node was present but unusable. Comparing against this value cannot drift
     * from the digest spelling the key used.
     */
    public static String fingerprintDigest(JsonNode certificateProperties) {
        JsonNode fingerprint = certificateProperties == null ? null : certificateProperties.get("fingerprint");
        if (fingerprint == null || !fingerprint.isObject() || !isPresent(fingerprint.get(CbomNames.CONTENT))) {
            return null;
        }
        JsonNode algorithm = fingerprint.get("alg");
        String label = algorithm != null && !algorithm.isNull() ? algorithm.asText() : "sha-256";
        return AsciiText.fold(AsciiText.strip(label)) + ":"
                + AsciiText.fold(AsciiText.strip(fingerprint.get(CbomNames.CONTENT).asText()));
    }

    public static List<String> claimed(JsonNode component, JsonNode certificateProperties) {
        List<String> digests = new ArrayList<>();
        String fromFingerprint = fingerprintDigest(certificateProperties);
        if (fromFingerprint != null) {
            digests.add(fromFingerprint);
        }
        String fromComponent = componentHash(component);
        if (fromComponent != null) {
            digests.add(fromComponent);
        }
        return digests;
    }

    /**
     * The strongest available content hash from {@code component.hashes[]}.
     *
     * <p>
     * Measured present on 6 of 6 real certificates, and byte-identical to Core's own thumbprint convention -- SHA-256
     * over the full DER, lowercase hex -- which makes a future correlation a one-line equijoin instead of a backfill.
     */
    public static String componentHash(JsonNode component) {
        JsonNode hashes = component == null ? null : component.get("hashes");
        if (hashes == null || !hashes.isArray()) {
            return null;
        }
        Map<String, String> byAlgorithm = new LinkedHashMap<>();
        for (JsonNode hash : hashes) {
            if (!hash.isObject()) {
                continue;
            }
            JsonNode algorithm = hash.get("alg");
            JsonNode content = hash.get("content");
            byAlgorithm
                    .put(AsciiText.upper(algorithm == null || algorithm.isNull() ? "" : algorithm.asText()),
                            AsciiText.fold(content == null || content.isNull() ? "" : content.asText()));
        }
        for (String algorithm : PREFERENCE) {
            String content = byAlgorithm.get(algorithm);
            if (content != null && !content.isEmpty()) {
                return AsciiText.fold(algorithm) + ":" + content;
            }
        }
        return null;
    }

    static boolean isPresent(JsonNode node) {
        return node != null && !node.isNull() && !node.asText().isEmpty();
    }
}
