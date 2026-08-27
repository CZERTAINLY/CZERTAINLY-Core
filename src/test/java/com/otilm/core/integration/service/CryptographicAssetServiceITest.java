package com.otilm.core.integration.service;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import com.otilm.api.model.core.search.FilterConditionOperator;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SortDirection;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.dao.entity.Cbom;
import com.otilm.core.dao.repository.CbomRepository;
import com.otilm.core.enums.FilterField;
import com.otilm.core.model.cbom.CryptoAssetIdentityGuard;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.CryptographicAssetExternalService;
import com.otilm.core.service.writer.cbom.CryptoAssetSourceWriter;
import com.otilm.core.service.writer.cbom.CryptoAssetWriter;
import com.otilm.core.util.BaseSpringBootTest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.otilm.core.util.builders.SearchFilterRequestDtoBuilder.aPropertyEqualsFilter;
import static com.otilm.core.util.builders.SearchFilterRequestDtoBuilder.aPropertyFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The list operation ({@link CryptographicAssetExternalService#listCryptographicAssets}) end to end, through the
 * service interface rather than the repository directly: ordering and paging determinism, the 1000-item clamp and
 * paging refusals, the list-row-to-DTO mapping, and the acceptance criteria from the issue itself -- filters that
 * answer across asset types, and a refuted OID that stays out of a default free-text search until the caller opts into
 * the facet.
 */
class CryptographicAssetServiceITest extends BaseSpringBootTest {

    @Autowired
    private CryptographicAssetExternalService cryptographicAssetService;

    @Autowired
    private CryptoAssetWriter assetWriter;

    @Autowired
    private CryptoAssetSourceWriter sourceWriter;

    @Autowired
    private CbomRepository cbomRepository;

    @Test
    void ordersByNameThenUuidAndPagesDeterministically() {
        UUID aesA = seedNamed(CryptographicAssetType.ALGORITHM, "AES", "oid-aes-a");
        UUID aesB = seedNamed(CryptographicAssetType.ALGORITHM, "  aes  ", "oid-aes-b");
        UUID ecdsa = seedNamed(CryptographicAssetType.ALGORITHM, "ECDSA", "oid-ecdsa");
        UUID nullNamed = seedNamed(CryptographicAssetType.CERTIFICATE, null, "oid-null-name");

        List<UUID> aesByUuidAscending = sortedByUuidString(aesA, aesB);
        UUID aesFirst = aesByUuidAscending.get(0);
        UUID aesSecond = aesByUuidAscending.get(1);

        PaginationResponseDto<CryptographicAssetDto> defaultPage = list(new SearchRequestDto());
        assertThat(defaultPage.getItems())
                .extracting(CryptographicAssetDto::getUuid)
                .containsExactly(aesFirst, aesSecond, ecdsa, nullNamed);
        assertThat(defaultPage.getPageNumber()).isEqualTo(1);
        assertThat(defaultPage.getItemsPerPage()).isEqualTo(10);
        assertThat(defaultPage.getTotalItems()).isEqualTo(4);
        assertThat(defaultPage.getTotalPages()).isEqualTo(1);

        SearchRequestDto firstOfTwo = new SearchRequestDto();
        firstOfTwo.setItemsPerPage(2);
        firstOfTwo.setPageNumber(1);
        PaginationResponseDto<CryptographicAssetDto> page1 = list(firstOfTwo);
        assertThat(page1.getItems()).extracting(CryptographicAssetDto::getUuid).containsExactly(aesFirst, aesSecond);
        assertThat(page1.getTotalPages()).isEqualTo(2);

        SearchRequestDto secondOfTwo = new SearchRequestDto();
        secondOfTwo.setItemsPerPage(2);
        secondOfTwo.setPageNumber(2);
        PaginationResponseDto<CryptographicAssetDto> page2 = list(secondOfTwo);
        assertThat(page2.getItems())
                .describedAs("page 1's boundary falls inside the tied 'aes' name group -- the uuid tiebreak makes "
                        + "it deterministic")
                .extracting(CryptographicAssetDto::getUuid)
                .containsExactly(ecdsa, nullNamed);
    }

    @Test
    void clampsOversizedPageAndRefusesInvalidPagingOrSort() {
        SearchRequestDto oversized = new SearchRequestDto();
        oversized.setItemsPerPage(5000);
        assertThat(list(oversized).getItemsPerPage()).isEqualTo(1000);

        SearchRequestDto zeroPage = new SearchRequestDto();
        zeroPage.setPageNumber(0);
        assertThatThrownBy(() -> list(zeroPage)).isInstanceOf(ValidationException.class);

        SearchRequestDto zeroItems = new SearchRequestDto();
        zeroItems.setItemsPerPage(0);
        assertThatThrownBy(() -> list(zeroItems)).isInstanceOf(ValidationException.class);

        SearchRequestDto sorted = new SearchRequestDto();
        sorted
                .setSort(new SearchSortRequestDto(FilterFieldSource.PROPERTY, FilterField.CBOM_ASSET_NAME.name(),
                        SortDirection.ASC));
        assertThatThrownBy(() -> list(sorted)).isInstanceOf(ValidationException.class).hasMessageContaining("Sorting");
    }

    @Test
    void mapsListRowsToDtos() {
        UUID sourced = seedNamed(CryptographicAssetType.ALGORITHM, "AES", "oid-sourced");
        Cbom cbomOne = newCbom("urn:uuid:svc-one");
        Cbom cbomTwo = newCbom("urn:uuid:svc-two");
        sourceWriter
                .upsertSource(sourced, cbomOne.getUuid(), Map.of("k", "v"),
                        List.of(Map.of("location", "a"), Map.of("location", "b"), Map.of("location", "c")),
                        OffsetDateTime.now());
        sourceWriter
                .upsertSource(sourced, cbomTwo.getUuid(), Map.of("k", "v"),
                        List.of(Map.of("location", "d"), Map.of("location", "e")), OffsetDateTime.now());
        assetWriter.applyPqcVerdict(sourced, PqcVerdict.NOT_READY, "rule", "reason", 3, Map.of());

        UUID guarded = assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE, "some-cn", null, null,
                        null, null, null, null, null, null), CryptoAssetIdentityGuard.BARE_CN_SUBJECT);

        UUID neverEvaluated = seedNamed(CryptographicAssetType.ALGORITHM, "RSA", "oid-never-evaluated");

        PaginationResponseDto<CryptographicAssetDto> page = list(new SearchRequestDto());

        CryptographicAssetDto sourcedDto = dtoFor(page, sourced);
        assertThat(sourcedDto.getSourceCbomCount()).isEqualTo(2);
        assertThat(sourcedDto.getOccurrenceCount())
                .describedAs("occurrences summed across both sources: 3 + 2")
                .isEqualTo(5);
        assertThat(sourcedDto.getPqcVerdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(sourcedDto.isQuarantined()).isFalse();
        assertThat(sourcedDto.getName()).describedAs("canonical spelling, not the seeded case").isEqualTo("aes");
        assertThat(sourcedDto.getUuid()).isEqualTo(sourced);

        CryptographicAssetDto guardedDto = dtoFor(page, guarded);
        assertThat(guardedDto.isQuarantined()).isTrue();

        CryptographicAssetDto neverEvaluatedDto = dtoFor(page, neverEvaluated);
        assertThat(neverEvaluatedDto.getPqcVerdict())
                .describedAs("never evaluated -> UNKNOWN, not null")
                .isEqualTo(PqcVerdict.UNKNOWN);
    }

    /**
     * The issue's "normalized fields answer for every asset type" criterion: one filter value on a shared field returns
     * rows of different {@link CryptographicAssetType}s, and the reported total tracks the filtered count.
     */
    @Test
    void filtersNarrowResultsAcrossAssetTypes() {
        UUID algorithmRow = assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "ECDSA", null, "ecdsa",
                        "signature", "P-256", "secp256r1", null, null, null), null);
        UUID certificateRow = assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE, "some-cert", null,
                        null, null, null, "secp256r1", null, null, null), null);
        seedNamed(CryptographicAssetType.ALGORITHM, "RSA", "oid-unrelated");

        SearchRequestDto request = new SearchRequestDto();
        request.setFilters(List.of(aPropertyEqualsFilter(FilterField.CBOM_ASSET_CURVE, "secp256r1")));
        PaginationResponseDto<CryptographicAssetDto> page = list(request);

        assertThat(page.getItems())
                .extracting(CryptographicAssetDto::getUuid)
                .containsExactlyInAnyOrder(algorithmRow, certificateRow);
        assertThat(page.getTotalItems()).describedAs("the filtered count, not the whole table").isEqualTo(2);
    }

    /**
     * The issue's own acceptance criterion, end to end through the service: a refuted OID must never let a default
     * free-text search surface the row, but opting into the refuted-OID facet lets it answer again.
     */
    @Test
    void freeTextRefusesARefutedOidUntilTheRefutedFacetOptsIn() {
        UUID refuted = assetWriter
                .upsertIdentity(
                        new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "ML-KEM-768",
                                "2.16.840.1.101.3.4.4.2", "ml-kem", "kem", null, null, null, null, null),
                        CryptoAssetIdentityGuard.REFUTED_OID);

        SearchRequestDto freeTextOnly = new SearchRequestDto();
        freeTextOnly
                .setFilters(List
                        .of(aPropertyFilter(FilterField.CBOM_ASSET_FREE_TEXT, FilterConditionOperator.CONTAINS,
                                "101.3.4.4")));
        PaginationResponseDto<CryptographicAssetDto> withoutOptIn = list(freeTextOnly);
        assertThat(withoutOptIn.getItems()).isEmpty();
        assertThat(withoutOptIn.getTotalItems()).isZero();

        SearchRequestDto withOptIn = new SearchRequestDto();
        withOptIn
                .setFilters(List
                        .of(aPropertyFilter(FilterField.CBOM_ASSET_FREE_TEXT, FilterConditionOperator.CONTAINS,
                                "101.3.4.4"), aPropertyEqualsFilter(FilterField.CBOM_ASSET_OID_REFUTED, "true")));
        PaginationResponseDto<CryptographicAssetDto> withOptInResult = list(withOptIn);
        assertThat(withOptInResult.getItems()).extracting(CryptographicAssetDto::getUuid).containsExactly(refuted);
    }

    @Test
    void totalPagesReflectsFilteredCountAcrossPages() {
        seedFamily("AES-1", "aes");
        seedFamily("AES-2", "aes");
        seedFamily("AES-3", "aes");
        seedFamily("RSA-1", "rsa");

        SearchRequestDto request = new SearchRequestDto();
        request.setItemsPerPage(2);
        request.setPageNumber(1);
        request.setFilters(List.of(aPropertyEqualsFilter(FilterField.CBOM_ASSET_ALGORITHM_FAMILY, "aes")));
        PaginationResponseDto<CryptographicAssetDto> page1 = list(request);
        assertThat(page1.getItems()).hasSize(2);
        assertThat(page1.getTotalItems()).isEqualTo(3);
        assertThat(page1.getTotalPages()).isEqualTo(2);

        request.setPageNumber(2);
        PaginationResponseDto<CryptographicAssetDto> page2 = list(request);
        assertThat(page2.getItems()).hasSize(1);
        assertThat(page2.getTotalItems()).isEqualTo(3);
    }

    // ---- helpers ----

    private PaginationResponseDto<CryptographicAssetDto> list(SearchRequestDto request) {
        return cryptographicAssetService.listCryptographicAssets(SecurityFilter.create(), request);
    }

    private UUID seedNamed(CryptographicAssetType type, String name, String oid) {
        return assetWriter
                .upsertIdentity(
                        new CryptoAssetIdentityFields(type, name, oid, null, null, null, null, null, null, null), null);
    }

    private UUID seedFamily(String name, String family) {
        return assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, name, null, family,
                        null, null, null, null, null, null), null);
    }

    private Cbom newCbom(String serialNumber) {
        Cbom cbom = new Cbom();
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(1);
        cbom.setSpecVersion("1.7");
        return cbomRepository.save(cbom);
    }

    private static CryptographicAssetDto dtoFor(PaginationResponseDto<CryptographicAssetDto> page, UUID uuid) {
        return page.getItems().stream().filter(dto -> dto.getUuid().equals(uuid)).findFirst().orElseThrow();
    }

    /**
     * The given UUIDs, ascending in Postgres's own {@code uuid} order -- see
     * {@code CryptoAssetListQueryITest#sortedByUuidString} for why {@link UUID#toString()} order, not
     * {@link UUID#compareTo}, is what Postgres actually applies.
     */
    private static List<UUID> sortedByUuidString(UUID... uuids) {
        return Arrays.stream(uuids).sorted(Comparator.comparing(UUID::toString)).toList();
    }
}
