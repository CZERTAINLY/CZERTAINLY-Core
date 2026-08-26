package com.otilm.core.integration.cbom;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.common.BulkActionMessageDto;
import com.otilm.api.model.core.cbom.CbomAssetSyncState;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import com.otilm.core.cbom.asset.CryptoAssetIdentityCalculator;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.dao.CryptoAssetConstraintTranslator;
import com.otilm.core.dao.entity.Cbom;
import com.otilm.core.dao.entity.cbom.CbomTombstone;
import com.otilm.core.dao.entity.cbom.CryptoAsset;
import com.otilm.core.dao.entity.cbom.CryptoAssetAlias;
import com.otilm.core.dao.entity.cbom.CryptoAssetSource;
import com.otilm.core.dao.repository.CbomRepository;
import com.otilm.core.dao.repository.cbom.CbomTombstoneRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetAliasRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetSourceRepository;
import com.otilm.core.model.cbom.CryptoAssetIdentityGuard;
import com.otilm.core.service.CbomExternalService;
import com.otilm.core.service.writer.cbom.CbomAssetSyncStateWriter;
import com.otilm.core.service.writer.cbom.CbomTombstoneWriter;
import com.otilm.core.service.writer.cbom.CryptoAssetAliasWriter;
import com.otilm.core.service.writer.cbom.CryptoAssetSourceWriter;
import com.otilm.core.service.writer.cbom.CryptoAssetWriter;
import com.otilm.core.util.BaseSpringBootTest;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The cryptographic asset inventory against real PostgreSQL: the upsert paths, the merge election, the foreign-key
 * behaviours the schema promises, and the alias table's invisibility to identity.
 */
class CryptoAssetInventoryITest extends BaseSpringBootTest {

    private static final String SECRET_MARKER = "AKIA-SECRET-MARKER";

    @Autowired
    private CryptoAssetRepository assetRepository;

    @Autowired
    private CryptoAssetSourceRepository sourceRepository;

    @Autowired
    private CryptoAssetAliasRepository aliasRepository;

    @Autowired
    private CbomTombstoneRepository tombstoneRepository;

    @Autowired
    private CbomRepository cbomRepository;

    @Autowired
    private CbomExternalService cbomService;

    @Autowired
    private CryptoAssetWriter assetWriter;

    @Autowired
    private CryptoAssetSourceWriter sourceWriter;

    @Autowired
    private CryptoAssetAliasWriter aliasWriter;

    @Autowired
    private CbomAssetSyncStateWriter syncStateWriter;

    @Autowired
    private CbomTombstoneWriter tombstoneWriter;

    private Cbom leanCbom;
    private Cbom richCbom;

    @BeforeEach
    void seedCboms() {
        leanCbom = cbom("urn:uuid:lean", 1);
        richCbom = cbom("urn:uuid:rich", 1);
    }

    // ---- upsert and the rule-set stamp ----

    @Test
    void reIngestingTheSameAssetKeepsOneRow() {
        UUID first = assetWriter.upsertIdentity(rsa2048(), null);
        UUID second = assetWriter.upsertIdentity(rsa2048(), null);

        assertThat(second).isEqualTo(first);
        assertThat(assetRepository.count()).isEqualTo(1);
    }

    @Test
    void differentAssetsGetDifferentRows() {
        assetWriter.upsertIdentity(rsa2048(), null);
        assetWriter.upsertIdentity(algorithm("RSA", "4096"), null);

        assertThat(assetRepository.count()).isEqualTo(2);
    }

    @Test
    void everyUpsertStampsTheRulesetVersionThatKeyedTheRow() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);

        assertThat(asset(assetUuid).getRulesetVersion()).isEqualTo(CryptoAssetIdentityCalculator.RULESET_VERSION);

        // A row left behind by an older rule set: findable by query, which is what recording the version buys.
        CryptoAsset stale = asset(assetUuid);
        stale.setRulesetVersion(CryptoAssetIdentityCalculator.RULESET_VERSION - 1);
        assetRepository.save(stale);

        assertThat(assetRepository.findUuidsKeyedBefore(CryptoAssetIdentityCalculator.RULESET_VERSION))
                .containsExactly(assetUuid);

        // The update branch of the upsert restamps it, so a re-sync clears the backlog.
        assertThat(assetWriter.upsertIdentity(rsa2048(), null)).isEqualTo(assetUuid);
        assertThat(assetRepository.findUuidsKeyedBefore(CryptoAssetIdentityCalculator.RULESET_VERSION)).isEmpty();
        assertThat(asset(assetUuid).getRulesetVersion()).isEqualTo(CryptoAssetIdentityCalculator.RULESET_VERSION);
    }

    @Test
    void aPqcVerdictLeavesTheIdentityStampAlone() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        CryptoAsset before = asset(assetUuid);

        assetWriter
                .applyPqcVerdict(assetUuid, PqcVerdict.NOT_READY, "RSA-CLASSICAL", "RSA is not quantum resistant", 7,
                        Map.of("algorithmFamily", "rsa", "parameterSetIdentifier", "2048"));

        CryptoAsset after = asset(assetUuid);
        assertThat(after.getPqcVerdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(after.getPqcRuleId()).isEqualTo("RSA-CLASSICAL");
        assertThat(after.getPqcRulesetVersion()).isEqualTo(7);
        assertThat(after.getPqcEvaluatedFields()).containsEntry("parameterSetIdentifier", "2048");
        assertThat(after.getRulesetVersion())
                .describedAs("a verdict is not an identity")
                .isEqualTo(before.getRulesetVersion());
        assertThat(after.getIdentityKey()).isEqualTo(before.getIdentityKey());
    }

    // ---- the merge and its provenance pointer ----

    @Test
    void theMergedPayloadIsTheRichestSourcePayloadAndSaysWhichSourceItCameFrom() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        Map<String, Object> lean = Map.of("primitive", "signature");
        Map<String, Object> rich = Map.of("primitive", "signature", "padding", "pkcs1v15", "parameterSet", "2048");

        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), lean, List.of(), OffsetDateTime.now());
        sourceWriter.upsertSource(assetUuid, richCbom.getUuid(), rich, List.of(), OffsetDateTime.now());

        CryptoAsset merged = asset(assetUuid);
        assertThat(merged.getSourceCount()).isEqualTo(2);
        assertThat(merged.getMergedCryptoProperties()).isEqualTo(rich);
        assertThat(merged.getPropertiesLeafCount()).isEqualTo(3);
        assertThat(merged.getPropertiesHash()).isNotNull();
        assertThat(merged.getPropertiesSourceUuid())
                .describedAs("the merged payload must be attributable to the source it was adopted from")
                .isEqualTo(source(assetUuid, richCbom.getUuid()).getUuid());
    }

    @Test
    void detachingTheElectedSourceReElectsFromWhatIsLeft() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        Map<String, Object> lean = Map.of("primitive", "signature");
        Map<String, Object> rich = Map.of("primitive", "signature", "padding", "pkcs1v15");
        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), lean, List.of(), OffsetDateTime.now());
        sourceWriter.upsertSource(assetUuid, richCbom.getUuid(), rich, List.of(), OffsetDateTime.now());

        assertThat(sourceWriter.detachCbom(assetUuid, richCbom.getUuid())).isEqualTo(1);

        CryptoAsset merged = asset(assetUuid);
        assertThat(merged.getSourceCount()).isEqualTo(1);
        assertThat(merged.getMergedCryptoProperties()).isEqualTo(lean);
        assertThat(merged.getPropertiesSourceUuid()).isEqualTo(source(assetUuid, leanCbom.getUuid()).getUuid());
    }

    @Test
    void detachingTheLastSourceLeavesTheAssetWithNothingToAttribute() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        sourceWriter
                .upsertSource(assetUuid, leanCbom.getUuid(), Map.of("primitive", "signature"), List.of(),
                        OffsetDateTime.now());

        sourceWriter.detachCbom(assetUuid, leanCbom.getUuid());

        CryptoAsset stripped = asset(assetUuid);
        assertThat(stripped.getSourceCount()).isZero();
        assertThat(stripped.getMergedCryptoProperties()).isNull();
        assertThat(stripped.getPropertiesHash()).isNull();
        assertThat(stripped.getPropertiesLeafCount()).isZero();
        assertThat(stripped.getPropertiesSourceUuid()).isNull();
        assertThat(assetRepository.findById(assetUuid))
                .describedAs("retention is reversible by a later sweep; deletion is not, and the re-sync semantics "
                        + "are not ratified")
                .isPresent();
    }

    @Test
    void deletingASourceRowNullsThePointerRatherThanLeavingItDangling() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        sourceWriter
                .upsertSource(assetUuid, leanCbom.getUuid(), Map.of("primitive", "signature"), List.of(),
                        OffsetDateTime.now());
        UUID sourceUuid = source(assetUuid, leanCbom.getUuid()).getUuid();
        assertThat(asset(assetUuid).getPropertiesSourceUuid()).isEqualTo(sourceUuid);

        // Deliberately bypassing the writer: this asserts the schema's own guarantee, not the writer's discipline.
        sourceRepository.deleteById(sourceUuid);

        assertThat(asset(assetUuid).getPropertiesSourceUuid())
                .describedAs("a NULL pointer is findable; a dangling uuid is indistinguishable from a valid one")
                .isNull();
    }

    @Test
    void reIngestingASourceKeepsWhenItWasFirstSeen() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        OffsetDateTime first = OffsetDateTime.now().minusDays(3);
        OffsetDateTime later = OffsetDateTime.now();

        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), Map.of("primitive", "signature"), List.of(), first);
        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), Map.of("primitive", "keyAgree"), List.of(), later);

        CryptoAssetSource stored = source(assetUuid, leanCbom.getUuid());
        assertThat(sourceRepository.count()).isEqualTo(1);
        assertThat(stored.getFirstSeenAt()).isCloseTo(first, within(1, ChronoUnit.SECONDS));
        assertThat(stored.getLastSeenAt()).isCloseTo(later, within(1, ChronoUnit.SECONDS));
        assertThat(stored.getOriginalCryptoProperties()).containsEntry("primitive", "keyAgree");
    }

    @Test
    void aSourceThatReportedNoPropertiesLeavesTheMergeBookkeepingEmpty() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);

        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), null, null, OffsetDateTime.now());

        CryptoAssetSource stored = source(assetUuid, leanCbom.getUuid());
        assertThat(stored.getOriginalCryptoProperties()).isNull();
        assertThat(stored.getEvidence()).isNull();
        assertThat(stored.getOccurrenceCount()).isZero();
        assertThat(stored.getPropertiesHash()).isNull();

        CryptoAsset merged = asset(assetUuid);
        assertThat(merged.getSourceCount()).isEqualTo(1);
        assertThat(merged.getMergedCryptoProperties()).isNull();
        assertThat(merged.getPropertiesHash())
                .describedAs("ck_crypto_asset_properties_pair requires payload and hash to be absent together")
                .isNull();
        assertThat(merged.getPropertiesSourceUuid())
                .describedAs("an absent payload is still attributable to the source that reported nothing")
                .isEqualTo(stored.getUuid());
    }

    @Test
    void anEquallyRichPairElectsDeterministically() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        Map<String, Object> one = Map.of("primitive", "signature");
        Map<String, Object> other = Map.of("primitive", "keyAgree");
        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), one, List.of(), OffsetDateTime.now());
        sourceWriter.upsertSource(assetUuid, richCbom.getUuid(), other, List.of(), OffsetDateTime.now());
        Map<String, Object> firstElection = asset(assetUuid).getMergedCryptoProperties();

        // Re-ingesting in the opposite order must not flip the election; without a total order the pointer would
        // oscillate on every sync.
        sourceWriter.upsertSource(assetUuid, richCbom.getUuid(), other, List.of(), OffsetDateTime.now());
        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), one, List.of(), OffsetDateTime.now());

        assertThat(asset(assetUuid).getMergedCryptoProperties()).isEqualTo(firstElection);
    }

    @Test
    void aVerdictCanBeCleared() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        assetWriter.applyPqcVerdict(assetUuid, PqcVerdict.NOT_READY, "RSA-CLASSICAL", "reason", 7, Map.of("a", "b"));

        assetWriter.applyPqcVerdict(assetUuid, null, null, null, 8, null);

        CryptoAsset cleared = asset(assetUuid);
        assertThat(cleared.getPqcVerdict()).isNull();
        assertThat(cleared.getPqcRuleId()).isNull();
        assertThat(cleared.getPqcEvaluatedFields()).isNull();
        assertThat(cleared.getPqcRulesetVersion()).isEqualTo(8);
    }

    // ---- evidence capping ----

    @Test
    void storedEvidenceIsCappedAndTheOccurrenceCountStillRecordsWhatWasSeen() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Map<String, Object> occurrence = new LinkedHashMap<>();
            occurrence.put("location", "src/file" + i + ".java");
            occurrence.put("additionalContext", SECRET_MARKER + "x".repeat(4096));
            occurrences.add(occurrence);
        }

        sourceWriter
                .upsertSource(assetUuid, leanCbom.getUuid(), Map.of("primitive", "signature"), occurrences,
                        OffsetDateTime.now());

        CryptoAssetSource stored = source(assetUuid, leanCbom.getUuid());
        assertThat(stored.getOccurrenceCount()).isEqualTo(60);
        assertThat(stored.getEvidence()).hasSize(50);
        assertThat(stored.getEvidence())
                .describedAs("the snippet is dropped whole, never shortened")
                .allSatisfy(occurrence -> assertThat(occurrence).doesNotContainKey("additionalContext"));
        assertThat(String.valueOf(stored.getEvidence())).doesNotContain(SECRET_MARKER);
    }

    // ---- foreign-key behaviour ----

    @Test
    void deletingAnAssetTakesItsSourcesAndAliasesWithIt() {
        UUID absorbedUuid = assetWriter.upsertIdentity(algorithm("RSA", "2048"), null);
        UUID canonicalUuid = assetWriter.upsertIdentity(algorithm("RSA", "4096"), null);
        sourceWriter
                .upsertSource(canonicalUuid, leanCbom.getUuid(), Map.of("primitive", "signature"), List.of(),
                        OffsetDateTime.now());
        aliasWriter
                .record(asset(absorbedUuid).getIdentityKey(), asset(canonicalUuid).getIdentityKey(), "typo",
                        "operator");
        assertThat(aliasRepository.count()).isEqualTo(1);

        assertThat(assetWriter.delete(canonicalUuid)).isEqualTo(1);

        assertThat(sourceRepository.count())
                .describedAs("a source reference is meaningless without its asset")
                .isZero();
        assertThat(aliasRepository.count()).describedAs("an alias pointing at nothing is a lie").isZero();
        assertThat(cbomRepository.findById(leanCbom.getUuid()))
                .describedAs("deleting an asset must not touch the CBOM it came from")
                .isPresent();
    }

    @Test
    void aCbomStillReferencedByTheInventoryCannotBeDeletedAndTheRefusalLeaksNothing() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        sourceWriter
                .upsertSource(assetUuid, leanCbom.getUuid(), Map.of("primitive", "signature"), List.of(),
                        OffsetDateTime.now());

        DataIntegrityViolationException rejection = (DataIntegrityViolationException) org.junit.jupiter.api.Assertions
                .assertThrows(DataIntegrityViolationException.class,
                        () -> cbomRepository.deleteById(leanCbom.getUuid()));

        assertThat(CryptoAssetConstraintTranslator.constraintNameOf(rejection))
                .contains("crypto_asset_source_to_cbom_key");
        String description = CryptoAssetConstraintTranslator.describe(rejection);
        assertThat(description).contains("still referenced by the cryptographic asset inventory");
        assertThat(description)
                .describedAs("the driver's DETAIL line quotes the failing row")
                .doesNotContain("Detail")
                .doesNotContain("DETAIL")
                .doesNotContain(leanCbom.getUuid().toString())
                .doesNotContain(asset(assetUuid).getIdentityKey());

        // Detaching first is what makes the deletion possible, which is what "through the service path" means.
        sourceWriter.detachCbom(assetUuid, leanCbom.getUuid());
        cbomRepository.deleteById(leanCbom.getUuid());
        assertThat(cbomRepository.findById(leanCbom.getUuid())).isEmpty();
    }

    @Test
    void theCbomDeleteServicePathRefusesWhileTheInventoryReferencesItAndForwardsNoDriverText() throws Exception {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        sourceWriter
                .upsertSource(assetUuid, leanCbom.getUuid(), Map.of("primitive", "signature"), List.of(),
                        OffsetDateTime.now());
        String identityKey = asset(assetUuid).getIdentityKey();

        ValidationException refusal = org.junit.jupiter.api.Assertions
                .assertThrows(ValidationException.class, () -> cbomService.deleteCbom(leanCbom.getUuid()));

        assertThat(refusal.getMessage()).contains("still referenced by the cryptographic asset inventory");
        assertLeaksNothing(refusal.getMessage(), identityKey);

        List<BulkActionMessageDto> messages = cbomService.bulkDeleteCbom(List.of(leanCbom.getUuid()));

        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.getMessage()).contains("still referenced by the cryptographic asset inventory");
            assertLeaksNothing(message.getMessage(), identityKey);
        });
        assertThat(cbomRepository.findById(leanCbom.getUuid()))
                .describedAs("a refused deletion leaves the row where it was")
                .isPresent();
    }

    private void assertLeaksNothing(String text, String identityKey) {
        assertThat(text)
                .describedAs("the driver's DETAIL line quotes the failing row, and for crypto_asset that row carries "
                        + "the identity key")
                .doesNotContain("Detail")
                .doesNotContain("DETAIL")
                .doesNotContain("detail")
                .doesNotContain(identityKey)
                .doesNotContain(leanCbom.getUuid().toString())
                .doesNotContain("crypto_asset_source");
    }

    // ---- the alias table is invisible to identity ----

    @Test
    void addingRemovingAndRePointingAnAliasChangesNoIdentityKey() {
        UUID absorbedUuid = assetWriter.upsertIdentity(algorithm("RSA", "2048"), null);
        UUID canonicalUuid = assetWriter.upsertIdentity(algorithm("RSA", "4096"), null);
        UUID otherUuid = assetWriter.upsertIdentity(algorithm("ECDSA", "P-256"), null);
        Map<UUID, String> keysBefore = Map
                .of(absorbedUuid, asset(absorbedUuid).getIdentityKey(), canonicalUuid,
                        asset(canonicalUuid).getIdentityKey(), otherUuid, asset(otherUuid).getIdentityKey());

        aliasWriter.record(keysBefore.get(absorbedUuid), keysBefore.get(canonicalUuid), "duplicate", "operator");
        assertKeysUnchanged(keysBefore);

        aliasWriter.record(keysBefore.get(absorbedUuid), keysBefore.get(otherUuid), "re-pointed", "operator");
        assertThat(aliasRepository.count())
                .describedAs("re-pointing replaces the decision, it does not add one")
                .isEqualTo(1);
        assertThat(aliasRepository.resolveCanonicalKey(keysBefore.get(absorbedUuid)))
                .contains(keysBefore.get(otherUuid));
        assertKeysUnchanged(keysBefore);

        assertThat(aliasWriter.remove(keysBefore.get(absorbedUuid))).isEqualTo(1);
        assertKeysUnchanged(keysBefore);

        // And keying is unaffected: the same fields still land on the same row, alias or no alias.
        assertThat(assetWriter.upsertIdentity(algorithm("RSA", "2048"), null)).isEqualTo(absorbedUuid);
    }

    /**
     * A re-sync that arrives out of order must not invert the window. Keeping {@code first_seen_at} and assigning
     * {@code last_seen_at} looks equivalent only while events arrive in order; a retry or a replayed document can
     * present an older timestamp after a newer one.
     */
    @Test
    void anOutOfOrderResyncStillLeavesARealSeenAtWindow() {
        UUID assetUuid = assetWriter.upsertIdentity(rsa2048(), null);
        OffsetDateTime later = OffsetDateTime.now();
        OffsetDateTime earlier = later.minusDays(2);

        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), null, null, later);
        sourceWriter.upsertSource(assetUuid, leanCbom.getUuid(), null, null, earlier);

        CryptoAssetSource stored = source(assetUuid, leanCbom.getUuid());
        assertThat(stored.getFirstSeenAt())
                .describedAs("an older event moves first_seen_at earlier")
                .isCloseTo(earlier, within(1, ChronoUnit.SECONDS));
        assertThat(stored.getLastSeenAt())
                .describedAs("an older event does not drag last_seen_at back")
                .isCloseTo(later, within(1, ChronoUnit.SECONDS));
        assertThat(stored.getLastSeenAt())
                .describedAs("the window is never inverted")
                .isAfterOrEqualTo(stored.getFirstSeenAt());
    }

    /**
     * A guard is a safety refusal, not a field: {@link CryptoAssetAliasWriter} reads the guard that is on the row now
     * to decide whether a merge is allowed. If ordinary re-ingest could clear it, the refusal would evaporate on the
     * next unguarded report of the same normalized identity.
     */
    @Test
    void aLaterUnguardedReportDoesNotClearASafetyGuard() {
        CryptoAssetIdentityFields bareCnFields = new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE,
                "example.com", null, null, null, null, null, null, null, null);
        UUID bareCn = assetWriter.upsertIdentity(bareCnFields, CryptoAssetIdentityGuard.BARE_CN_SUBJECT);

        assertThat(assetWriter.upsertIdentity(bareCnFields, null))
                .describedAs("the same normalized identity lands on the same row")
                .isEqualTo(bareCn);
        assertThat(asset(bareCn).getIdentityGuard())
                .describedAs("re-ingest without a guard must not lift one that was set")
                .isEqualTo(CryptoAssetIdentityGuard.BARE_CN_SUBJECT);

        UUID fullDn = assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE,
                        "CN=example.com,O=Example,C=CZ", null, null, null, null, null, null, null, null), null);
        String bareCnKey = asset(bareCn).getIdentityKey();
        String fullDnKey = asset(fullDn).getIdentityKey();

        assertThatThrownBy(() -> aliasWriter.record(bareCnKey, fullDnKey, "still looks the same", "operator"))
                .describedAs("the refusal survives the re-ingest that would otherwise have cleared the guard")
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BARE_CN_SUBJECT");
    }

    @Test
    void anAliasIsRefusedWhereASafetyRuleCausedTheSplit() {
        UUID bareCn = assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE, "example.com", null,
                        null, null, null, null, null, null, null), CryptoAssetIdentityGuard.BARE_CN_SUBJECT);
        UUID fullDn = assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE,
                        "CN=example.com,O=Example,C=CZ", null, null, null, null, null, null, null, null), null);
        String bareCnKey = asset(bareCn).getIdentityKey();
        String fullDnKey = asset(fullDn).getIdentityKey();

        assertThatThrownBy(
                () -> aliasWriter.record(bareCnKey, fullDnKey, "looks like the same certificate", "operator"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BARE_CN_SUBJECT");
        assertThat(aliasRepository.count()).isZero();

        assertThatThrownBy(() -> aliasWriter.record(fullDnKey, bareCnKey, "the other way round", "operator"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BARE_CN_SUBJECT");
        assertThat(aliasRepository.count()).isZero();
    }

    @Test
    void anAliasIsRefusedWhenItWouldFormAChainOrPointAtItself() {
        UUID first = assetWriter.upsertIdentity(algorithm("RSA", "2048"), null);
        UUID second = assetWriter.upsertIdentity(algorithm("RSA", "4096"), null);
        UUID third = assetWriter.upsertIdentity(algorithm("RSA", "8192"), null);
        String firstKey = asset(first).getIdentityKey();
        String secondKey = asset(second).getIdentityKey();
        String thirdKey = asset(third).getIdentityKey();

        aliasWriter.record(firstKey, secondKey, "duplicate", "operator");

        assertThatThrownBy(() -> aliasWriter.record(secondKey, thirdKey, "chained", "operator"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("chains");
        assertThatThrownBy(() -> aliasWriter.record(thirdKey, thirdKey, "itself", "operator"))
                .isInstanceOf(ValidationException.class);
        assertThat(aliasRepository.findAll()).extracting(CryptoAssetAlias::getAbsorbedKey).containsExactly(firstKey);
    }

    @Test
    void anAliasIsRefusedWhenThereIsNothingToDecideAboutOrAKeyIsMissing() {
        String existing = asset(assetWriter.upsertIdentity(rsa2048(), null)).getIdentityKey();

        assertThatThrownBy(() -> aliasWriter.record("never-ingested", existing, "typo", "operator"))
                .describedAs("a redirect from a key no asset and no alias carries is one no ingest can follow")
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no cryptographic asset to absorb");
        assertThatThrownBy(() -> aliasWriter.record(null, existing, "typo", "operator"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("two distinct asset keys");
        assertThatThrownBy(() -> aliasWriter.record(existing, null, "typo", "operator"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("two distinct asset keys");
        assertThat(aliasRepository.count()).isZero();
    }

    @Test
    void removingAnAliasThatIsNotThereChangesNothing() {
        assertThat(aliasWriter.remove("never-recorded")).isZero();
    }

    // ---- CBOM-level asset bookkeeping ----

    @Test
    void theSyncStateWriterMovesACbomThroughIngest() {
        assertThat(cbom(leanCbom.getUuid()).getAssetSyncState())
                .describedAs("an existing header-only row has never had its assets ingested")
                .isEqualTo(CbomAssetSyncState.PENDING);

        syncStateWriter.markInProgress(leanCbom.getUuid());
        assertThat(cbom(leanCbom.getUuid()).getAssetSyncState()).isEqualTo(CbomAssetSyncState.IN_PROGRESS);

        syncStateWriter.markFailed(leanCbom.getUuid(), "The CBOM document could not be parsed.");
        Cbom failed = cbom(leanCbom.getUuid());
        assertThat(failed.getAssetSyncState()).isEqualTo(CbomAssetSyncState.FAILED);
        assertThat(failed.getAssetSyncError()).isEqualTo("The CBOM document could not be parsed.");
        assertThat(failed.getAssetsSyncedAt()).isNull();

        OffsetDateTime syncedAt = OffsetDateTime.now();
        syncStateWriter.markSynced(leanCbom.getUuid(), syncedAt);
        Cbom synced = cbom(leanCbom.getUuid());
        assertThat(synced.getAssetSyncState()).isEqualTo(CbomAssetSyncState.SYNCED);
        assertThat(synced.getAssetSyncError()).describedAs("a success clears the previous failure").isNull();
        assertThat(synced.getAssetsSyncedAt()).isNotNull();
    }

    @Test
    void aDeletedCbomIsTombstonedOnceAndByItsOwnUuid() {
        tombstoneWriter.record(leanCbom.getUuid(), "urn:uuid:lean", 1, OffsetDateTime.now(), "operator");
        tombstoneWriter.record(leanCbom.getUuid(), "urn:uuid:lean", 1, OffsetDateTime.now(), "someone else");

        assertThat(tombstoneRepository.findAll())
                .describedAs("a retried deletion has nothing to add to the first record of it")
                .singleElement()
                .satisfies(tombstone -> {
                    assertThat(tombstone.getUuid()).isEqualTo(leanCbom.getUuid());
                    assertThat(tombstone.getDeletedBy()).isEqualTo("operator");
                });
        assertThat(tombstoneRepository.existsBySerialNumberAndVersion("urn:uuid:lean", 1)).isTrue();
        assertThat(tombstoneRepository.findById(leanCbom.getUuid())).map(CbomTombstone::getVersion).contains(1);
    }

    // ---- helpers ----

    private void assertKeysUnchanged(Map<UUID, String> keysBefore) {
        keysBefore
                .forEach((uuid, key) -> assertThat(asset(uuid).getIdentityKey())
                        .describedAs("no alias operation may move an asset's identity")
                        .isEqualTo(key));
    }

    private static CryptoAssetIdentityFields rsa2048() {
        return algorithm("RSA", "2048");
    }

    private static CryptoAssetIdentityFields algorithm(String name, String parameterSet) {
        return new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, name, null,
                name.toLowerCase(java.util.Locale.ROOT), "signature", parameterSet, null, null, null, null);
    }

    private CryptoAsset asset(UUID uuid) {
        return assetRepository.findById(uuid).orElseThrow();
    }

    private CryptoAssetSource source(UUID assetUuid, UUID cbomUuid) {
        return sourceRepository.findByAssetUuidAndCbomUuid(assetUuid, cbomUuid).orElseThrow();
    }

    private Cbom cbom(UUID uuid) {
        return cbomRepository.findById(uuid).orElseThrow();
    }

    private Cbom cbom(String serialNumber, int version) {
        Cbom cbom = new Cbom();
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(version);
        cbom.setSpecVersion("1.7");
        Optional<Cbom> saved = Optional.of(cbomRepository.save(cbom));
        return saved.orElseThrow();
    }
}
