package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The ratified identity and normalization decision tables. Data only, no behaviour.
 *
 * <p>
 * The tables are <em>data, never code</em>: they load from {@code cbom/identity-tables.json}, the same artifact the
 * reference implementation reads, so a vocabulary change is a reviewed data change rather than a code change in two
 * languages. The shipped file's SHA-256 is {@code 1331969bb507...}, which is the hash every published
 * cross-implementation agreement figure was measured against -- quote an agreement number only with the artifact hash
 * it was taken against, because a number measured before a fix is a historical number, not a current one.
 *
 * <p>
 * Every lookup here is fold-insensitive by way of {@link AsciiText#lookupKey}: a producer writing {@code aes} or
 * {@code md5} means the registry's {@code AES} and {@code MD5}, and keying the lowercase spelling verbatim split real
 * assets from every other producer's identical algorithm. What enters a key is always the <em>table's</em> spelling.
 */
public final class IdentityTables {

    private static final String RESOURCE = "cbom/identity-tables.json";

    /**
     * Curve spellings the registry does not list in bare form but producers write anyway. Carried here rather than in
     * the JSON because the reference carries them in code too, and a divergence in this set silently changes which
     * digit runs the parameter-set parser is allowed to read.
     */
    private static final List<String> EXTRA_CURVE_SPELLINGS = List
            .of("P-224", "P-256", "P-384", "P-521", "P-192", "nistp256", "nistp384", "nistp521", "x25519", "x448",
                    "ed25519", "ed448", "curve25519", "curve448", "prime256v1");

    private final Set<String> families;
    private final Map<String, Set<String>> pseudoFamilies;
    private final Map<String, String> curveCanonical;
    private final Map<String, String> curveAliases;
    private final Map<String, OidEntry> oidToFamily;
    private final Set<String> oidBlockedPrefixes;
    private final List<GrammarRule> nameGrammar;
    private final List<String> sizeStoplist;
    private final List<String> modeTokens;
    private final List<Pattern> cipherSuitePatterns;
    private final List<SecondaryMarker> secondaryMarkers;
    private final Pattern curveStrip;
    private final Pattern stoplistStrip;
    private final List<String> paddingTokens;
    private final Map<String, String> paddingAliases;
    private final Set<String> variantVocabulary;
    private final Map<String, String> variantSynonyms;
    private final Set<String> truncatableFamilies;
    private final Set<String> sentinels;
    private final Map<String, String> primitiveDefaults;
    private final Map<String, String> familyTokens;
    private final Map<String, String> dnAttributeOids;
    private final Map<String, Integer> nameIntrinsicSizes;
    private final List<String> curveSpellingsByLength;
    private final int sizeMin;
    private final int sizeMax;

    /** One row of the ordered name grammar: the family a spelling names, and the two guard forms it is matched with. */
    public record GrammarRule(Pattern strict, Pattern loose, Pattern unguarded, String family) {
    }

    /** A secondary construction token the name carries beside its family. */
    public record SecondaryMarker(String label, Pattern pattern) {
    }

    /** What an OID arc says about an asset. Every field beyond the family is enrichment and may be absent. */
    public record OidEntry(String family, Integer parameterSet, String mode, String curve, String primitive,
            String matchedArc, List<String> residualArcs) {

        OidEntry matchedAt(String arc, List<String> residual) {
            return new OidEntry(family, parameterSet, mode, curve, primitive, arc, residual);
        }
    }

    private IdentityTables(JsonNode raw) {
        this.families = textSet(raw.get("algorithmFamilies"));
        this.pseudoFamilies = raw
                .get("pseudoFamilies")
                .properties()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> textSet(entry.getValue())));
        this.curveCanonical = textMap(raw.get("curveCanonical"));
        Map<String, String> aliases = new HashMap<>();
        raw
                .get("curveAliases")
                .properties()
                .forEach(entry -> aliases.put(AsciiText.lookupKey(entry.getKey()), entry.getValue().asText()));
        this.curveAliases = Map.copyOf(aliases);
        this.oidToFamily = oidEntries(raw.get("oidToFamily"));
        this.oidBlockedPrefixes = textSet(raw.get("oidBlockedPrefixes"));
        this.nameGrammar = grammar(raw.get("nameGrammar"));
        this.sizeStoplist = textList(raw.get("sizeStoplist"));
        this.modeTokens = textList(raw.get("modeTokens"));
        this.cipherSuitePatterns = textList(raw.get("cipherSuiteNamePatterns"))
                .stream()
                .map(pattern -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                .toList();
        this.secondaryMarkers = markers(raw.get("secondaryMarkers"));
        this.paddingTokens = textList(raw.get("paddingTokens"));
        this.paddingAliases = textMap(raw.get("paddingAliases"));
        this.variantVocabulary = textSet(raw.get("variantVocabulary"));
        this.variantSynonyms = textMap(raw.get("variantSynonyms"));
        this.truncatableFamilies = textSet(raw.get("truncatableFamilies"));
        this.sentinels = textList(raw.get("sentinels"))
                .stream()
                .map(AsciiText::fold)
                .collect(Collectors.toUnmodifiableSet());
        this.primitiveDefaults = textMap(raw.get("primitiveDefaults"));
        this.dnAttributeOids = textMap(raw.get("dnShortNames"));
        Map<String, Integer> intrinsics = new LinkedHashMap<>();
        raw
                .get("nameIntrinsicSizes")
                .properties()
                .forEach(entry -> intrinsics.put(AsciiText.fold(entry.getKey()), entry.getValue().asInt()));
        // Insertion order is load-bearing, and Map.copyOf does not keep it. The intrinsic lookup is first-match-wins
        // over this map, and one name can carry two of its tokens: `X25519/X448` must take 256 from the x25519 it
        // mentions first, and `Ed25519/Ed448` likewise, rather than the 448 or 456 the second token would give. An
        // unordered map turned that into whichever bucket the hash happened to fill first.
        this.nameIntrinsicSizes = Collections.unmodifiableMap(intrinsics);
        this.sizeMin = raw.get("sizeWhitelist").get("min").asInt();
        this.sizeMax = raw.get("sizeWhitelist").get("max").asInt();

        Map<String, String> tokens = new HashMap<>();
        pseudoFamilies.keySet().forEach(token -> tokens.put(AsciiText.lookupKey(token), token));
        families.forEach(token -> tokens.put(AsciiText.lookupKey(token), token));
        this.familyTokens = Map.copyOf(tokens);

        this.curveSpellingsByLength = curveSpellings();
        this.curveStrip = Pattern
                .compile("(?<![A-Za-z0-9])(?:" + alternation(curveStripTokens()) + ")(?![A-Za-z0-9])",
                        Pattern.CASE_INSENSITIVE);
        // Unguarded on the left on purpose: in `ChaCha20Poly1305` the Poly1305 token is preceded by a digit, so a
        // left-guarded strip leaves it in place and 1305 is then read as a key size. Stripping is not matching.
        this.stoplistStrip = Pattern
                .compile("(?:" + alternation(sizeStoplist) + ")(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    }

    /**
     * Loads the ratified tables from the classpath. Fails loudly: a missing table is a broken build, not a runtime
     * state.
     */
    public static IdentityTables load() {
        try (InputStream stream = IdentityTables.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "The ratified identity tables are missing from the classpath: " + RESOURCE);
            }
            return new IdentityTables(new ObjectMapper().readTree(stream));
        } catch (IOException e) {
            throw new IllegalStateException("The ratified identity tables could not be read: " + RESOURCE, e);
        }
    }

    /** True when the value is one of the ratified "producer said nothing" spellings, which are treated as absent. */
    public boolean isSentinel(String value) {
        return value != null && sentinels.contains(AsciiText.fold(value.strip()));
    }

    /** True when {@code pseudo} is a pseudo-family that {@code concrete} belongs to. */
    public boolean subsumes(String pseudo, String concrete) {
        return pseudo != null && concrete != null && pseudoFamilies.getOrDefault(pseudo, Set.of()).contains(concrete);
    }

    /**
     * The legal family token a producer's declaration names, or {@code null}.
     *
     * <p>
     * A value outside the vocabulary contributes nothing to the key and derivation proceeds by name, so the asset lands
     * where its siblings are. Measured: 11 corpus assets declare values outside the vocabulary and 9 same-name groups
     * split because the declaration used to be taken verbatim.
     */
    public String familyToken(String declared) {
        return declared == null ? null : familyTokens.get(AsciiText.lookupKey(declared.strip()));
    }

    public Set<String> families() {
        return families;
    }

    public Map<String, Set<String>> pseudoFamilies() {
        return pseudoFamilies;
    }

    public Map<String, String> curveCanonical() {
        return curveCanonical;
    }

    public Map<String, String> curveAliases() {
        return curveAliases;
    }

    public Map<String, OidEntry> oidToFamily() {
        return oidToFamily;
    }

    public Set<String> oidBlockedPrefixes() {
        return oidBlockedPrefixes;
    }

    public List<GrammarRule> nameGrammar() {
        return nameGrammar;
    }

    public List<String> sizeStoplist() {
        return sizeStoplist;
    }

    public List<String> modeTokens() {
        return modeTokens;
    }

    public List<Pattern> cipherSuitePatterns() {
        return cipherSuitePatterns;
    }

    public List<SecondaryMarker> secondaryMarkers() {
        return secondaryMarkers;
    }

    public Pattern curveStrip() {
        return curveStrip;
    }

    public Pattern stoplistStrip() {
        return stoplistStrip;
    }

    public List<String> curveSpellingsByLength() {
        return curveSpellingsByLength;
    }

    public List<String> paddingTokens() {
        return paddingTokens;
    }

    public Map<String, String> paddingAliases() {
        return paddingAliases;
    }

    public Set<String> variantVocabulary() {
        return variantVocabulary;
    }

    public Map<String, String> variantSynonyms() {
        return variantSynonyms;
    }

    public Set<String> truncatableFamilies() {
        return truncatableFamilies;
    }

    public Map<String, String> primitiveDefaults() {
        return primitiveDefaults;
    }

    /**
     * Distinguished-name attribute types, short and long spellings alike, mapped to dotted OIDs.
     *
     * <p>
     * Read from the table rather than hardcoded, and that is not a style preference. Both implementations once carried
     * their own abbreviation-only copy, so a producer writing {@code commonName=} got a lower-cased short name in the
     * slot where the specification says a dotted OID always goes -- and because both sides reproduced the wart
     * together, no cross-implementation agreement measurement could ever have found it.
     */
    public Map<String, String> dnAttributeOids() {
        return dnAttributeOids;
    }

    public Map<String, Integer> nameIntrinsicSizes() {
        return nameIntrinsicSizes;
    }

    public int sizeMin() {
        return sizeMin;
    }

    public int sizeMax() {
        return sizeMax;
    }

    private List<String> curveStripTokens() {
        Set<String> tokens = new HashSet<>(EXTRA_CURVE_SPELLINGS);
        curveCanonical.keySet().forEach(token -> tokens.add(token.substring(token.indexOf('/') + 1)));
        return longestFirst(tokens);
    }

    private List<String> curveSpellings() {
        Set<String> spellings = new LinkedHashSet<>();
        curveCanonical.keySet().stream().filter(token -> !token.contains("/")).forEach(spellings::add);
        curveCanonical.keySet().forEach(token -> spellings.add(token.substring(token.indexOf('/') + 1)));
        spellings.addAll(EXTRA_CURVE_SPELLINGS);
        return longestFirst(spellings);
    }

    /** Longest first, so `P-256` is removed before any bare `256` can be read out of it. */
    private static List<String> longestFirst(Set<String> tokens) {
        List<String> ordered = new ArrayList<>(tokens);
        ordered.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        return List.copyOf(ordered);
    }

    /**
     * One alternation rather than a substitution per token: the per-token loops drove millions of calls over a corpus.
     */
    private static String alternation(List<String> tokens) {
        return tokens.stream().map(Pattern::quote).collect(Collectors.joining("|"));
    }

    private static List<GrammarRule> grammar(JsonNode node) {
        List<GrammarRule> rules = new ArrayList<>();
        for (JsonNode rule : node) {
            String pattern = rule.get("pattern").asText();
            // The loose form drops the LEFT word guard. The guard is right for deciding the winning family -- it stops
            // `design` matching DES -- but wrong for asking "does this name also mention a digest", because the real
            // spellings run the words together: `HMACMD5` merged with bare `HMAC` for exactly this reason.
            String loose = pattern.replace("(?<![A-Za-z0-9])", "");
            // The unguarded form drops the TRAILING guard too, used only to read the halves out of a hybrid name,
            // where producers glue tokens together with no separator at all and a right-hand guard hides `X25519`.
            String unguarded = loose.replaceAll("\\(\\?![^)]*\\)$", "");
            rules
                    .add(new GrammarRule(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE),
                            Pattern.compile(loose, Pattern.CASE_INSENSITIVE),
                            Pattern.compile(unguarded, Pattern.CASE_INSENSITIVE), rule.get("family").asText()));
        }
        return List.copyOf(rules);
    }

    private static List<SecondaryMarker> markers(JsonNode node) {
        List<SecondaryMarker> markers = new ArrayList<>();
        for (JsonNode marker : node) {
            markers
                    .add(new SecondaryMarker(marker.get(0).asText(),
                            Pattern.compile(marker.get(1).asText(), Pattern.CASE_INSENSITIVE)));
        }
        return List.copyOf(markers);
    }

    private static Map<String, OidEntry> oidEntries(JsonNode node) {
        Map<String, OidEntry> entries = new HashMap<>();
        node.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            entries
                    .put(entry.getKey(), new OidEntry(text(value, "family"), integer(value, "parameterSet"),
                            text(value, "mode"), text(value, "curve"), text(value, "primitive"), null, List.of()));
        });
        return Map.copyOf(entries);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static Set<String> textSet(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        node.forEach(element -> values.add(element.asText()));
        return Set.copyOf(values);
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(element -> values.add(element.asText()));
        return List.copyOf(values);
    }

    private static Map<String, String> textMap(JsonNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        node
                .properties()
                .forEach(entry -> values
                        .put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText()));
        return Collections.unmodifiableMap(values);
    }
}
