package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.otilm.core.serialization.ObjectMapperFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the ratified artifact the whole identity chain keys on.
 *
 * <p>
 * These are tripwires rather than behaviour tests. The tables are data, and a data change is a re-keying event for the
 * entire inventory -- which makes an unnoticed edit strictly worse than a code change, because nothing about it looks
 * like a change to the keying rules.
 */
class IdentityTablesTest {

    /**
     * The artifact the shipped build keys on. Every published cross-implementation agreement figure predates it and was
     * measured against {@code 1331969bb507...}.
     *
     * <p>
     * An agreement percentage is worthless without the hash of the artifact it was taken against: one figure was
     * published and stale within the hour, because the same day's fixes had replaced the snapshot it was measured on.
     * If this assertion fails, the tables were edited, every stored identity key is potentially stale, and the figures
     * quoted throughout this package no longer describe the shipped build. Re-run the vectors and ratify each moved
     * expectation before changing the value here.
     *
     * <p>
     * A table edit re-keys, but it does not by itself advance {@link IdentityRuleset#VERSION}: that stamp exists to
     * make stale <em>stored</em> rows findable, and nothing in production drives {@code CryptoAssetWriter} yet, so
     * generation 2 has never keyed a row. The bump belongs to whichever change first gives that writer a production
     * caller, and that change names the rulings it is advancing past.
     */
    @Test
    void theShippedTablesAreTheRatifiedArtifact() throws IOException {
        try (InputStream stream = IdentityTablesTest.class
                .getClassLoader()
                .getResourceAsStream("cbom/identity-tables.json")) {
            assertThat(stream).isNotNull();
            assertThat(IdentityDigests.sha256HexOfBytes(stream.readAllBytes()))
                    .describedAs("the decision tables are ratified data; editing them re-keys the inventory")
                    .isEqualTo("1f647c456c1fe503ba76c607132603fc9487b3862e4a016610aca6a53012abc2");
        }
    }

    /**
     * Insertion order survives loading, because the intrinsic-size lookup is first-match-wins.
     *
     * <p>
     * This is pinned because it has already broken once: {@code Map.copyOf} does not preserve order, and a name
     * carrying two intrinsic tokens then answered with whichever the hash happened to place first. The failure was
     * silent -- only names naming two curves were affected, and nothing about the code looked wrong.
     */
    @Test
    void theIntrinsicSizeTableKeepsItsOrder() {
        assertThat(new ArrayList<>(IdentityTables.load().nameIntrinsicSizes().keySet()))
                .containsExactly("ed25519", "x25519", "curve25519", "ed448", "x448", "curve448");
    }

    /**
     * The distinguished-name mapping is read from the tables and carries the long spellings.
     *
     * <p>
     * A table shipping is not a table being consumed: this exact map was once shipped, asked for, delivered, and then
     * not read, because the consumer kept a hardcoded guess beside it. The abbreviation-only version is what produced a
     * lower-cased short name where the specification says a dotted OID always goes.
     */
    @Test
    void theDistinguishedNameMappingIsConsumedFromTheTables() {
        IdentityTables tables = IdentityTables.load();

        assertThat(tables.dnAttributeOids())
                .containsEntry("cn", "2.5.4.3")
                .containsEntry("commonname", "2.5.4.3")
                .containsEntry("organizationname", "2.5.4.10")
                .containsEntry("domaincomponent", "0.9.2342.19200300.100.1.25");
    }

    /**
     * Every family a pseudo-family claims to contain must be a real registry token, or subsumption silently misfires.
     */
    @Test
    void everyPseudoFamilyMemberIsARegistryToken() {
        IdentityTables tables = IdentityTables.load();
        List<String> unknown = new ArrayList<>();
        tables
                .pseudoFamilies()
                .forEach((pseudo, members) -> members
                        .stream()
                        .filter(member -> !tables.families().contains(member))
                        .forEach(member -> unknown.add(pseudo + " -> " + member)));

        assertThat(unknown).isEmpty();
    }

    /** A family a producer can declare must resolve fold-insensitively, or the declaration is keyed verbatim. */
    @Test
    void everyFamilyTokenResolvesThroughAFoldedLookup() {
        IdentityTables tables = IdentityTables.load();
        List<String> unresolved = new ArrayList<>();
        tables.families().forEach(family -> {
            if (!family.equals(tables.familyToken(family))
                    || !family.equals(tables.familyToken(family.toLowerCase(Locale.ROOT)))) {
                unresolved.add(family);
            }
        });

        assertThat(unresolved).isEmpty();
    }

    /** Aliases must never point at a spelling outside the canonical curve vocabulary. */
    @Test
    void everyCurveAliasTargetsACanonicalCurve() {
        IdentityTables tables = IdentityTables.load();
        Set<String> canonicalCurves = new HashSet<>(tables.curveCanonical().values());

        assertThat(tables.curveAliases())
                .allSatisfy((alias, target) -> assertThat(canonicalCurves)
                        .describedAs("curve alias %s targets a canonical curve", alias)
                        .contains(target));
    }

    /**
     * OID-derived families are ratified table output, so every non-null family must resolve through the same lookup.
     */
    @Test
    void everyOidFamilyResolvesThroughAFoldedLookup() {
        IdentityTables tables = IdentityTables.load();
        List<String> unresolved = new ArrayList<>();

        tables.oidToFamily().forEach((oid, entry) -> {
            String family = entry.family();
            if (family != null && !family.equals(tables.familyToken(family))) {
                unresolved.add(oid + " -> " + family);
            }
        });

        assertThat(unresolved).isEmpty();
    }

    /** Primitive defaults must be total over concrete values that CycloneDX 1.6 can express. */
    @Test
    void everyPrimitiveDefaultUsesAnExpressiblePrimitive() throws IOException {
        JsonNode raw = rawTables();
        Set<String> expressible = textSet(raw.get("primitivesExpressibleIn16"));
        IdentityTables tables = IdentityTables.load();
        Map<String, String> defaults = tables.primitiveDefaults();

        assertThat(defaults.keySet())
                .allSatisfy(family -> assertThat(tables.familyToken(family))
                        .describedAs("primitive default key is a registered family")
                        .isEqualTo(family));
        assertThat(defaults.values())
                .allSatisfy(primitive -> assertThat(expressible)
                        .describedAs("primitive default value is expressible in CycloneDX 1.6")
                        .contains(primitive));
    }

    /** The size whitelist is the one the specification names; both bounds feed the parameter-set parser. */
    @Test
    void theSizeWhitelistIsTheRatifiedRange() {
        IdentityTables tables = IdentityTables.load();

        assertThat(tables.sizeMin()).isEqualTo(64);
        assertThat(tables.sizeMax()).isEqualTo(16384);
    }

    /** A missing table is a broken build, not a runtime condition, so loading fails loudly rather than degrading. */
    @Test
    void theTablesLoadOrTheBuildIsBroken() {
        assertThat(IdentityTables.load()).isNotNull();
        assertThat(IdentityTables.load().nameGrammar()).isNotEmpty();
        assertThat(IdentityTables.load().curveCanonical()).isNotEmpty();
    }

    private static JsonNode rawTables() throws IOException {
        try (InputStream stream = IdentityTablesTest.class
                .getClassLoader()
                .getResourceAsStream("cbom/identity-tables.json")) {
            assertThat(stream).isNotNull();
            return ObjectMapperFactory.storage().readTree(stream);
        }
    }

    private static Set<String> textSet(JsonNode node) {
        Set<String> values = new HashSet<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }
}
