package com.otilm.core.cbom.asset;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Computes a cryptographic asset's identity key: SHA-256 over a framed preimage built from
 * {@link CryptoAssetIdentityFields} and nothing else.
 *
 * <p>
 * Three properties are designed in deliberately, because each failure mode is silent corruption rather than a crash:
 *
 * <ol>
 * <li><b>Locale independence.</b> Case folding uses {@link Locale#ROOT}, never the platform default. A Turkish-locale
 * JVM folds {@code I} to {@code i-without-dot}, so a default-locale fold would key the same asset differently on two
 * nodes of one cluster -- and the unique arbiter cannot catch that, because the two keys genuinely differ.</li>
 * <li><b>Unicode normalisation.</b> Fields are normalised to NFKC <em>before</em> folding, then folded, then normalised
 * again, so a producer emitting a fullwidth, decomposed or compatibility spelling of an identifier keys onto the same
 * row. NFKC rather than NFC because these are machine identifiers, where compatibility equivalence is the equivalence
 * intended. The order is not interchangeable: a compatibility character has no case mapping of its own, so folding
 * first leaves {@code U+1D400 MATHEMATICAL BOLD CAPITAL A} untouched and NFKC then yields {@code A} -- an uppercase
 * result out of a case-folding normaliser, keyed differently from the plain {@code a} it is equivalent to. The second
 * pass is there because a full case mapping can itself emit an unnormalised sequence -- {@code U+0130 LATIN CAPITAL
 * LETTER I WITH DOT ABOVE} folds to {@code i} plus a combining dot above -- so the composed and already-folded
 * spellings are brought back together. NFKC-fold-NFKC is the approximation of Unicode's {@code NFKC_Casefold} reachable
 * without a case-folding API, and like {@code NFKC_Casefold} it leaves that dotted {@code i} distinct from a plain one:
 * that is the intended Unicode behaviour, not a shortfall of this pipeline.</li>
 * <li><b>No delimiter injection.</b> Fields are length-prefixed rather than separator-joined, so no field value can
 * forge a field boundary: a name of {@code "3:foo"} cannot impersonate the field after it. A separator scheme would
 * have needed a character forbidden in the inputs, and producer-supplied text admits no such character.</li>
 * </ol>
 *
 * <p>
 * A field that is blank after trimming is <em>absent</em>, and absent is distinct from every present value: producers
 * omit a field and emit {@code ""} interchangeably, and treating those as different identities would split the
 * inventory on a formatting choice.
 *
 * <p>
 * {@link #RULESET_VERSION} is recorded on the row but deliberately kept out of the preimage. Folding it in would re-key
 * every row on a rule-set bump -- re-migrating the whole inventory and invalidating every stored reference -- whereas
 * recording it makes staleness a query ({@code ruleset_version < current}).
 */
public final class CryptoAssetIdentityCalculator {

    /**
     * The identity rule-set generation this build keys with. Bump it whenever the preimage or a field's meaning moves.
     */
    public static final int RULESET_VERSION = 1;

    /** Domain separation, so this hash cannot be confused with any other SHA-256 the platform computes. */
    private static final String DOMAIN_TAG = "otilm:cbom-asset-identity";

    /** Sole encoding of an absent field. Not a valid length, so it can never be confused with a present one. */
    private static final byte ABSENT = '-';

    private static final byte LENGTH_TERMINATOR = ':';

    private CryptoAssetIdentityCalculator() {
    }

    /** The asset's identity key: lowercase SHA-256 hex over {@link #preimage(CryptoAssetIdentityFields)}. */
    public static String calculate(CryptoAssetIdentityFields fields) {
        return HexFormat.of().formatHex(sha256(preimage(fields)));
    }

    /**
     * The framed preimage. Package-private so a test can assert the framing directly -- an identity function whose
     * intermediate form is unobservable can only be tested for collisions it happens to have.
     */
    static byte[] preimage(CryptoAssetIdentityFields fields) {
        ByteArrayOutputStream preimage = new ByteArrayOutputStream();
        frame(preimage, DOMAIN_TAG);
        // Not normalised: the asset type is this platform's own enum constant, already canonical. Running the
        // producer-text normaliser over it would imply it is producer text.
        frame(preimage, fields.assetType() == null ? null : fields.assetType().name());
        frame(preimage, normalize(fields.name()));
        frame(preimage, normalize(fields.oid()));
        frame(preimage, normalize(fields.algorithmFamily()));
        frame(preimage, normalize(fields.primitive()));
        frame(preimage, normalize(fields.parameterSet()));
        frame(preimage, normalize(fields.curve()));
        frame(preimage, normalize(fields.mode()));
        frame(preimage, normalize(fields.padding()));
        frame(preimage, normalize(fields.variant()));
        return preimage.toByteArray();
    }

    /**
     * Folds and normalises one producer-supplied field, or returns {@code null} for a field that carries no value.
     *
     * @see CryptoAssetIdentityCalculator class documentation for why this order and these forms
     */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String folded = Normalizer.normalize(trimmed, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(folded, Normalizer.Form.NFKC);
    }

    private static void frame(ByteArrayOutputStream preimage, String value) {
        if (value == null) {
            preimage.write(ABSENT);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        preimage.writeBytes(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        preimage.write(LENGTH_TERMINATOR);
        preimage.writeBytes(bytes);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JCA specification; its absence is a broken JRE, not a runtime condition.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
