package com.otilm.core.integration.search;

import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.search.FilterConditionOperator;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.dao.entity.Cbom;
import com.otilm.core.dao.entity.cbom.CryptoAsset;
import com.otilm.core.dao.repository.CbomRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.enums.FilterField;
import com.otilm.core.model.cbom.CbomAssetSyncState;
import com.otilm.core.model.cbom.CryptographicAssetType;
import com.otilm.core.model.cbom.PqcVerdict;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.writer.cbom.CbomAssetSyncStateWriter;
import com.otilm.core.service.writer.cbom.CryptoAssetWriter;
import com.otilm.core.util.BaseSpringBootTest;
import com.otilm.core.util.FilterPredicatesBuilder;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.otilm.core.util.builders.SearchFilterRequestDtoBuilder.aPropertyEmptyFilter;
import static com.otilm.core.util.builders.SearchFilterRequestDtoBuilder.aPropertyEqualsFilter;
import static com.otilm.core.util.builders.SearchFilterRequestDtoBuilder.aPropertyFilter;
import static com.otilm.core.util.builders.SearchFilterRequestDtoBuilder.aPropertyNotEmptyFilter;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every crypto-asset search field, exercised against real PostgreSQL for the three cases a nullable column makes
 * distinct: a value match, no value at all, and any value.
 *
 * <p>
 * Nearly every identity column is nullable, because a producer may omit any of them, and a predicate that silently
 * fails to distinguish "absent" from "does not match" would make a filter quietly wrong rather than loudly broken. The
 * enum-backed columns are exercised too: the filter value arrives as text, and whether that text compares correctly
 * against an enum-mapped column is a fact about Hibernate, not something to assume.
 */
class CryptoAssetSearchITest extends BaseSpringBootTest {

    @Autowired
    private CryptoAssetRepository assetRepository;

    @Autowired
    private CryptoAssetWriter assetWriter;

    @Autowired
    private CbomRepository cbomRepository;

    @Autowired
    private CbomAssetSyncStateWriter syncStateWriter;

    private UUID populated;
    private UUID bare;

    @BeforeEach
    void seedAssets() {
        populated = assetWriter
                .upsertIdentity(
                        new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "ECDSA", "1.2.840.10045.4.3.2",
                                "ecdsa", "signature", "P-256", "secp256r1", "cbc", "pkcs1v15", "fips186-4"),
                        null);
        bare = assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE, null, null, null,
                        null, null, null, null, null, null), null);

        assetWriter
                .applyPqcVerdict(populated, PqcVerdict.NOT_READY, "ECDSA-CLASSICAL", "not quantum resistant", 3,
                        java.util.Map.of("curve", "P-256"));
    }

    // ---- the identity and filter columns ----

    @Test
    void everyNullableTextFieldDistinguishesAValueMatchFromAnAbsentValue() {
        assertFieldBehaviour(FilterField.CBOM_ASSET_NAME, "ECDSA");
        assertFieldBehaviour(FilterField.CBOM_ASSET_OID, "1.2.840.10045.4.3.2");
        assertFieldBehaviour(FilterField.CBOM_ASSET_ALGORITHM_FAMILY, "ecdsa");
        assertFieldBehaviour(FilterField.CBOM_ASSET_PRIMITIVE, "signature");
        assertFieldBehaviour(FilterField.CBOM_ASSET_PARAMETER_SET, "P-256");
        assertFieldBehaviour(FilterField.CBOM_ASSET_CURVE, "secp256r1");
        assertFieldBehaviour(FilterField.CBOM_ASSET_MODE, "cbc");
        assertFieldBehaviour(FilterField.CBOM_ASSET_PADDING, "pkcs1v15");
        assertFieldBehaviour(FilterField.CBOM_ASSET_VARIANT, "fips186-4");
    }

    @Test
    void theEnumBackedVerdictColumnComparesAgainstTextFromTheWire() {
        assertFieldBehaviour(FilterField.CBOM_ASSET_PQC_VERDICT, PqcVerdict.NOT_READY.name());
    }

    @Test
    void theNullablePqcRulesetVersionDistinguishesAValueMatchFromAnAbsentValue() {
        assertFieldBehaviour(FilterField.CBOM_ASSET_PQC_RULESET_VERSION, "3");
    }

    @Test
    void theNotNullAssetTypeMatchesByValueAndIsNeverEmpty() {
        assertThat(search(aPropertyEqualsFilter(FilterField.CBOM_ASSET_TYPE, CryptographicAssetType.ALGORITHM.name())))
                .containsExactly(populated);
        assertThat(
                search(aPropertyEqualsFilter(FilterField.CBOM_ASSET_TYPE, CryptographicAssetType.CERTIFICATE.name())))
                .containsExactly(bare);
        assertThat(search(aPropertyEmptyFilter(FilterField.CBOM_ASSET_TYPE))).isEmpty();
        assertThat(search(aPropertyNotEmptyFilter(FilterField.CBOM_ASSET_TYPE)))
                .containsExactlyInAnyOrder(populated, bare);
    }

    @Test
    void theNotNullNumericColumnsMatchByValueAndComparison() {
        assertThat(search(aPropertyEqualsFilter(FilterField.CBOM_ASSET_SOURCE_COUNT, "0")))
                .containsExactlyInAnyOrder(populated, bare);
        assertThat(search(aPropertyEqualsFilter(FilterField.CBOM_ASSET_SOURCE_COUNT, "1"))).isEmpty();
        assertThat(
                search(aPropertyFilter(FilterField.CBOM_ASSET_RULESET_VERSION, FilterConditionOperator.GREATER, "0")))
                .containsExactlyInAnyOrder(populated, bare);
        assertThat(search(aPropertyEmptyFilter(FilterField.CBOM_ASSET_RULESET_VERSION))).isEmpty();
    }

    @Test
    void aFilterCombinesWithAnother() {
        assertThat(searchAll(List
                .of(aPropertyEqualsFilter(FilterField.CBOM_ASSET_TYPE, CryptographicAssetType.ALGORITHM.name()),
                        aPropertyEqualsFilter(FilterField.CBOM_ASSET_CURVE, "secp256r1"))))
                .containsExactly(populated);
        assertThat(searchAll(List
                .of(aPropertyEqualsFilter(FilterField.CBOM_ASSET_TYPE, CryptographicAssetType.CERTIFICATE.name()),
                        aPropertyEqualsFilter(FilterField.CBOM_ASSET_CURVE, "secp256r1"))))
                .isEmpty();
    }

    // ---- the CBOM-rooted asset-sync fields ----

    @Test
    void theCbomAssetSyncFieldsAreRoutableToday() {
        Cbom pending = newCbom("urn:uuid:pending");
        Cbom synced = newCbom("urn:uuid:synced");
        OffsetDateTime syncedAt = OffsetDateTime.now();
        syncStateWriter.markSynced(synced.getUuid(), syncedAt);

        assertThat(FilterField.getEnumsForResource(Resource.CBOM))
                .contains(FilterField.CBOM_ASSET_SYNC_STATE, FilterField.CBOM_ASSETS_SYNCED_AT);

        assertThat(
                searchCbom(aPropertyEqualsFilter(FilterField.CBOM_ASSET_SYNC_STATE, CbomAssetSyncState.PENDING.name())))
                .containsExactly(pending.getUuid());
        assertThat(
                searchCbom(aPropertyEqualsFilter(FilterField.CBOM_ASSET_SYNC_STATE, CbomAssetSyncState.SYNCED.name())))
                .containsExactly(synced.getUuid());
        assertThat(searchCbom(aPropertyEmptyFilter(FilterField.CBOM_ASSETS_SYNCED_AT)))
                .containsExactly(pending.getUuid());
        assertThat(searchCbom(aPropertyNotEmptyFilter(FilterField.CBOM_ASSETS_SYNCED_AT)))
                .containsExactly(synced.getUuid());
        assertThat(searchCbom(aPropertyFilter(FilterField.CBOM_ASSETS_SYNCED_AT, FilterConditionOperator.LESSER,
                syncedAt.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")))))
                .containsExactly(synced.getUuid());
    }

    // ---- the interfaces#874 placeholder ----

    @Test
    void theCryptoAssetFieldsAreDeclaredButNotYetRoutable() {
        List<FilterField> cryptoAssetFields = Arrays
                .stream(FilterField.values())
                .filter(field -> field.getFieldAttribute() != null)
                .filter(field -> field.getFieldAttribute().getDeclaringType().getJavaType() == CryptoAsset.class)
                .toList();

        assertThat(cryptoAssetFields)
                .describedAs("the whole crypto-asset filter set is declared, and identity_key is not in it")
                .containsExactly(FilterField.CBOM_ASSET_TYPE, FilterField.CBOM_ASSET_NAME, FilterField.CBOM_ASSET_OID,
                        FilterField.CBOM_ASSET_ALGORITHM_FAMILY, FilterField.CBOM_ASSET_PRIMITIVE,
                        FilterField.CBOM_ASSET_PARAMETER_SET, FilterField.CBOM_ASSET_CURVE, FilterField.CBOM_ASSET_MODE,
                        FilterField.CBOM_ASSET_PADDING, FilterField.CBOM_ASSET_VARIANT,
                        FilterField.CBOM_ASSET_PQC_VERDICT, FilterField.CBOM_ASSET_PQC_RULESET_VERSION,
                        FilterField.CBOM_ASSET_RULESET_VERSION, FilterField.CBOM_ASSET_SOURCE_COUNT);
        assertThat(cryptoAssetFields)
                .describedAs("the ratified root resource is CBOM_ASSET, which interfaces#874 adds; until then a null "
                        + "root keeps these entries out of every resource's search surface")
                .allSatisfy(field -> assertThat(field.getRootResource()).isNull());

        for (Resource resource : Resource.values()) {
            assertThat(FilterField.getEnumsForResource(resource))
                    .describedAs("resource %s", resource)
                    .doesNotContainAnyElementsOf(cryptoAssetFields);
        }
    }

    // ---- helpers ----

    private void assertFieldBehaviour(FilterField field, Serializable matchingValue) {
        assertThat(search(aPropertyEqualsFilter(field, matchingValue)))
                .describedAs("EQUALS on %s", field)
                .containsExactly(populated);
        assertThat(search(aPropertyEmptyFilter(field))).describedAs("EMPTY on %s", field).containsExactly(bare);
        assertThat(search(aPropertyNotEmptyFilter(field)))
                .describedAs("NOT_EMPTY on %s", field)
                .containsExactly(populated);
    }

    private List<UUID> search(SearchFilterRequestDto filter) {
        return searchAll(List.of(filter));
    }

    private List<UUID> searchAll(List<SearchFilterRequestDto> filters) {
        return assetRepository
                .findUsingSecurityFilter(SecurityFilter.create(), List.of(),
                        (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, filters))
                .stream()
                .map(CryptoAsset::getUuid)
                .toList();
    }

    private List<UUID> searchCbom(SearchFilterRequestDto filter) {
        return cbomRepository
                .findUsingSecurityFilter(SecurityFilter.create(), List.of(),
                        (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, List.of(filter)))
                .stream()
                .map(Cbom::getUuid)
                .toList();
    }

    private Cbom newCbom(String serialNumber) {
        Cbom cbom = new Cbom();
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(1);
        cbom.setSpecVersion("1.7");
        return cbomRepository.save(cbom);
    }
}
