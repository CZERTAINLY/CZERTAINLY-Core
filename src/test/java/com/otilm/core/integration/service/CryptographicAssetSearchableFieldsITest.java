package com.otilm.core.integration.service;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.api.model.core.search.FilterConditionOperator;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.FilterFieldType;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.dao.entity.Cbom;
import com.otilm.core.dao.repository.CbomRepository;
import com.otilm.core.enums.FilterField;
import com.otilm.core.model.cbom.CryptoAssetIdentityGuard;
import com.otilm.core.service.CryptographicAssetExternalService;
import com.otilm.core.service.writer.cbom.CryptoAssetWriter;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CryptographicAssetExternalService#getSearchableFieldInformationByGroup()}: the wire-level shape of the
 * cryptographic asset search surface, including the two criteria the issue calls out explicitly -- the identity key is
 * never offered as a field, and a producer-spelling difference does not show up twice in a value list.
 */
class CryptographicAssetSearchableFieldsITest extends BaseSpringBootTest {

    private static final List<FilterField> EXPECTED_FIELDS = List
            .of(FilterField.CBOM_ASSET_TYPE, FilterField.CBOM_ASSET_NAME, FilterField.CBOM_ASSET_OID,
                    FilterField.CBOM_ASSET_ALGORITHM_FAMILY, FilterField.CBOM_ASSET_PRIMITIVE,
                    FilterField.CBOM_ASSET_PARAMETER_SET, FilterField.CBOM_ASSET_CURVE, FilterField.CBOM_ASSET_MODE,
                    FilterField.CBOM_ASSET_PADDING, FilterField.CBOM_ASSET_VARIANT, FilterField.CBOM_ASSET_PQC_VERDICT,
                    FilterField.CBOM_ASSET_PQC_RULESET_VERSION, FilterField.CBOM_ASSET_RULESET_VERSION,
                    FilterField.CBOM_ASSET_SOURCE_COUNT, FilterField.CBOM_ASSET_FREE_TEXT,
                    FilterField.CBOM_ASSET_OID_REFUTED, FilterField.CBOM_ASSET_SOURCE_CBOM);

    private static final String SEEDED_SERIAL = "urn:uuid:searchable-fields";

    @Autowired
    private CryptographicAssetExternalService cryptographicAssetService;

    @Autowired
    private CryptoAssetWriter assetWriter;

    @Autowired
    private CbomRepository cbomRepository;

    private List<SearchFieldDataByGroupDto> groups;

    private List<SearchFieldDataDto> fields;

    @BeforeEach
    void seedAssetsAndCbom() {
        assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "ECDSA",
                        "1.2.840.10045.4.3.2", "ecdsa", "signature", "P-256", "SECP256R1", null, null, null), null);
        // A second producer's spelling of the same curve, on a distinct row (different oid): findDistinctCurve()
        // must still report the canonical spelling once, not twice.
        assetWriter
                .upsertIdentity(
                        new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "ECDSA-VARIANT",
                                "1.2.840.10045.4.3.3", "ecdsa", "signature", "P-256", " secp256r1 ", null, null, null),
                        null);
        assetWriter
                .upsertIdentity(
                        new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "ML-KEM-768",
                                "2.16.840.1.101.3.4.4.2", "ml-kem", "kem", null, null, null, null, null),
                        CryptoAssetIdentityGuard.REFUTED_OID);

        Cbom cbom = new Cbom();
        cbom.setSerialNumber(SEEDED_SERIAL);
        cbom.setVersion(1);
        cbom.setSpecVersion("1.7");
        cbomRepository.save(cbom);

        groups = cryptographicAssetService.getSearchableFieldInformationByGroup();
        fields = groups.get(0).getSearchFieldData();
    }

    /**
     * The wire-level "the identity key is not offered" criterion, pinned by exact enumeration: nothing besides the 17
     * ratified crypto-asset fields is present.
     */
    @Test
    void oneGroupOffersExactlyTheCryptoAssetFields() {
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getFilterFieldSource()).isEqualTo(FilterFieldSource.PROPERTY);
        assertThat(fields)
                .extracting(SearchFieldDataDto::getFieldIdentifier)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_FIELDS.stream().map(FilterField::name).toList());
    }

    @Test
    void theCurveFieldOffersTheCanonicalSpellingOnlyOnce() {
        assertThat((List<String>) fieldFor(FilterField.CBOM_ASSET_CURVE).getValue())
                .containsExactly("secp256r1")
                .doesNotContainNull();
    }

    @Test
    void enumBackedFieldsCarryThePlatformEnumAndTheContractCodes() {
        SearchFieldDataDto type = fieldFor(FilterField.CBOM_ASSET_TYPE);
        assertThat(type.getPlatformEnum()).isNotNull();
        assertThat((List<String>) type.getValue()).contains("algorithm");

        SearchFieldDataDto verdict = fieldFor(FilterField.CBOM_ASSET_PQC_VERDICT);
        assertThat(verdict.getPlatformEnum()).isNotNull();
        assertThat((List<String>) verdict.getValue()).contains("notReady");
    }

    @Test
    void freeTextAndTheRefutedFacetOfferTheirOwnConditionsAndTypes() {
        assertThat(fieldFor(FilterField.CBOM_ASSET_FREE_TEXT).getConditions())
                .containsExactly(FilterConditionOperator.CONTAINS);

        SearchFieldDataDto refuted = fieldFor(FilterField.CBOM_ASSET_OID_REFUTED);
        assertThat(refuted.getConditions())
                .containsExactly(FilterConditionOperator.EQUALS, FilterConditionOperator.NOT_EQUALS);
        assertThat(refuted.getType()).isEqualTo(FilterFieldType.BOOLEAN);

        assertThat((List<String>) fieldFor(FilterField.CBOM_ASSET_SOURCE_CBOM).getValue())
                .containsExactly(SEEDED_SERIAL);
    }

    @Test
    void noFieldReportsSortable() {
        assertThat(fields)
                .describedAs(
                        "the list's order is fixed and the contract allows sorting only on fields marked " + "sortable")
                .allSatisfy(field -> assertThat(Boolean.TRUE.equals(field.getSortable())).isFalse());
    }

    private SearchFieldDataDto fieldFor(FilterField field) {
        return fields
                .stream()
                .filter(candidate -> candidate.getFieldIdentifier().equals(field.name()))
                .findFirst()
                .orElseThrow();
    }
}
