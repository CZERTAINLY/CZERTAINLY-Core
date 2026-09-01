package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The content digests a certificate claims, in preference order, reduced to one spelling. */
public final class CertificateDigests {

    /**
     * Preferred first, which is not strongest first: {@code SHA-256} leads because it is what producers actually emit,
     * and reordering would re-key every certificate claiming more than one digest. SHA-1 is last but still accepted --
     * a weak digest still identifies, and refusing it loses a row.
     */
    private static final List<String> PREFERENCE = List.of("SHA-256", "SHA-384", "SHA-512", "SHA-1");

    private CertificateDigests() {
    }

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
        return claim(AsciiText.fold(AsciiText.strip(label)),
                AsciiText.fold(AsciiText.strip(fingerprint.get(CbomNames.CONTENT).asText())));
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
        Set<String> contradicted = new HashSet<>();
        for (JsonNode hash : hashes) {
            if (!hash.isObject()) {
                continue;
            }
            JsonNode algorithm = hash.get("alg");
            JsonNode content = hash.get("content");
            String normalizedAlgorithm = AsciiText
                    .upper(AsciiText.strip(algorithm == null || algorithm.isNull() ? "" : algorithm.asText()));
            String normalizedContent = AsciiText
                    .fold(AsciiText.strip(content == null || content.isNull() ? "" : content.asText()));
            if (normalizedContent.isEmpty()) {
                continue;
            }
            String existing = byAlgorithm.putIfAbsent(normalizedAlgorithm, normalizedContent);
            if (existing != null && !existing.equals(normalizedContent)) {
                contradicted.add(normalizedAlgorithm);
            }
        }
        for (String algorithm : PREFERENCE) {
            String content = byAlgorithm.get(algorithm);
            if (content != null && !contradicted.contains(algorithm)) {
                return claim(AsciiText.fold(algorithm), content);
            }
        }
        return null;
    }

    /**
     * Joins an algorithm to its content so neither half can forge the boundary between them.
     *
     * <p>
     * {@code alg} is producer-controlled, so a bare join let {@code {"alg":"sha-256:aabbcc","content":"dd"}} and
     * {@code {"alg":"sha-256","content":"aabbcc:dd"}} render one string and collapse two certificates onto one key.
     *
     * <p>
     * The {@code :} belongs to this layer and not to {@link PreImageSlot}: the claim later enters a {@code |}-delimited
     * outer slot through {@link PreImageSlot#of}, and teaching that the {@code :} would escape this separator too,
     * erasing the distinction it exists to draw. The consequence is that the pre-image carries the doubly-escaped
     * spelling {@code %253A} rather than {@code %3A} -- see {@link PreImageSlot#escape}.
     */
    private static String claim(String algorithm, String content) {
        return PreImageSlot.escape(algorithm, CertificateDigests::digestEscapeFor) + ":"
                + PreImageSlot.escape(content, CertificateDigests::digestEscapeFor);
    }

    private static String digestEscapeFor(char character) {
        return switch (character) {
            case '%' -> "%25";
            case ':' -> "%3A";
            default -> null;
        };
    }

    static boolean isPresent(JsonNode node) {
        return node != null && !node.isNull() && !node.asText().isEmpty();
    }
}
