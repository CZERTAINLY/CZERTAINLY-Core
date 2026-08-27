package com.otilm.core.service.impl;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.NotSupportedException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.dashboard.CryptographicAssetStatisticsDto;
import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDetailDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDto;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import com.otilm.api.model.core.scheduler.PaginationRequestDto;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.core.comparator.SearchFieldDataComparator;
import com.otilm.core.dao.entity.cbom.CryptoAsset;
import com.otilm.core.dao.entity.cbom.CryptoAsset_;
import com.otilm.core.dao.repository.CbomRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.enums.FilterField;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.model.cbom.CryptoAssetListRow;
import com.otilm.core.security.authz.ExternalAuthorization;
import com.otilm.core.security.authz.SecuredUUID;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.CryptographicAssetExternalService;
import com.otilm.core.service.ResourceExtensionService;
import com.otilm.core.util.FilterPredicatesBuilder;
import com.otilm.core.util.RequestValidatorHelper;
import com.otilm.core.util.SearchHelper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Serves the ratified cryptographic asset inventory contract. List and searchable-fields are real reads over the
 * deduplicated cross-CBOM asset projection; detail, statistics and sync visibility remain {@link NotSupportedException}
 * refusals until core#2145 builds them. The service also serves the per-object listing behind {@code @AuthEndpoint} --
 * the role editor's object picker.
 *
 * <p>
 * {@link NotSupportedException} maps to HTTP 501 in {@code ExceptionHandlingAdvice}, so a permitted caller learns a
 * still-refused operation is not implemented while an unpermitted caller is refused with 403 by the authorization
 * aspect ahead of this body.
 */
@Service(Resource.Codes.CRYPTO_ASSET)
public class CryptographicAssetServiceImpl implements CryptographicAssetExternalService, ResourceExtensionService {

    private static final String NOT_IMPLEMENTED = "Cryptographic asset inventory is not implemented yet";

    // The OpenAPI schema documents 1000 as the maximum items per page; this is where it is actually enforced.
    private static final int MAX_ITEMS_PER_PAGE = 1000;

    private CryptoAssetRepository cryptoAssetRepository;

    private CbomRepository cbomRepository;

    @Autowired
    public void setCryptoAssetRepository(CryptoAssetRepository cryptoAssetRepository) {
        this.cryptoAssetRepository = cryptoAssetRepository;
    }

    @Autowired
    public void setCbomRepository(CbomRepository cbomRepository) {
        this.cbomRepository = cbomRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.LIST)
    public PaginationResponseDto<CryptographicAssetDto> listCryptographicAssets(SecurityFilter filter,
            SearchRequestDto request) {
        RequestValidatorHelper.revalidateSearchRequestDto(request);
        validatePaging(request);
        TriFunction<Root<CryptoAsset>, CriteriaBuilder, CriteriaQuery<?>, Predicate> where = (root, cb,
                criteriaQuery) -> FilterPredicatesBuilder
                        .getFiltersPredicate(cb, criteriaQuery, root, request.getFilters());
        Pageable page = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        // The documented order is name ASC, uuid ASC. Only the name term is spelled here: the repository appends
        // the ascending-uuid tiebreak to every paged secured query (SortOrderBuilder), which is what keeps page
        // boundaries deterministic inside a group of equal names.
        List<UUID> pageUuids = cryptoAssetRepository
                .findUuidsUsingSecurityFilter(filter, where, page, (root, cb) -> cb.asc(root.get(CryptoAsset_.name)));
        long totalItems = cryptoAssetRepository.countUsingSecurityFilter(filter, where);
        PaginationResponseDto<CryptographicAssetDto> response = new PaginationResponseDto<>();
        response.setItems(loadPage(pageUuids));
        response.setItemsPerPage(request.getItemsPerPage());
        response.setPageNumber(request.getPageNumber());
        response.setTotalItems(totalItems);
        response.setTotalPages((int) Math.ceil((double) totalItems / request.getItemsPerPage()));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.DETAIL)
    public CryptographicAssetDetailDto getCryptographicAsset(SecuredUUID uuid) throws NotFoundException {
        throw new NotSupportedException(NOT_IMPLEMENTED);
    }

    /**
     * The group is PROPERTY only: crypto assets carry no attribute-engine attributes, so offering CUSTOM/META groups
     * would advertise filter sources this resource does not serve. Curve values are canonical class representatives by
     * construction -- the column stores the normalized spelling. The enum-backed and boolean fields get their values
     * from {@link SearchHelper} (enum codes, or none).
     */
    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup() {
        List<SearchFieldDataDto> fields = List
                .of(SearchHelper.prepareSearch(FilterField.CBOM_ASSET_FREE_TEXT),
                        SearchHelper.prepareSearch(FilterField.CBOM_ASSET_TYPE),
                        SearchHelper.prepareSearch(FilterField.CBOM_ASSET_NAME),
                        SearchHelper.prepareSearch(FilterField.CBOM_ASSET_OID),
                        SearchHelper.prepareSearch(FilterField.CBOM_ASSET_OID_REFUTED),
                        SearchHelper
                                .prepareSearch(FilterField.CBOM_ASSET_ALGORITHM_FAMILY,
                                        cryptoAssetRepository.findDistinctAlgorithmFamily()),
                        SearchHelper
                                .prepareSearch(FilterField.CBOM_ASSET_PRIMITIVE,
                                        cryptoAssetRepository.findDistinctPrimitive()),
                        SearchHelper
                                .prepareSearch(FilterField.CBOM_ASSET_PARAMETER_SET,
                                        cryptoAssetRepository.findDistinctParameterSet()),
                        SearchHelper
                                .prepareSearch(FilterField.CBOM_ASSET_CURVE, cryptoAssetRepository.findDistinctCurve()),
                        SearchHelper
                                .prepareSearch(FilterField.CBOM_ASSET_MODE, cryptoAssetRepository.findDistinctMode()),
                        SearchHelper
                                .prepareSearch(FilterField.CBOM_ASSET_PADDING,
                                        cryptoAssetRepository.findDistinctPadding()),
                        SearchHelper
                                .prepareSearch(FilterField.CBOM_ASSET_VARIANT,
                                        cryptoAssetRepository.findDistinctVariant()),
                        SearchHelper.prepareSearch(FilterField.CBOM_ASSET_PQC_VERDICT),
                        SearchHelper.prepareSearch(FilterField.CBOM_ASSET_PQC_RULESET_VERSION),
                        SearchHelper.prepareSearch(FilterField.CBOM_ASSET_RULESET_VERSION),
                        SearchHelper.prepareSearch(FilterField.CBOM_ASSET_SOURCE_COUNT),
                        SearchHelper
                                .prepareSearch(FilterField.CBOM_ASSET_SOURCE_CBOM,
                                        cbomRepository.findDistinctSerialNumber()));
        List<SearchFieldDataDto> sorted = new ArrayList<>(fields);
        sorted.sort(new SearchFieldDataComparator());
        return List.of(new SearchFieldDataByGroupDto(sorted, FilterFieldSource.PROPERTY));
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.LIST)
    public CryptographicAssetStatisticsDto getCryptographicAssetStatistics() {
        throw new NotSupportedException(NOT_IMPLEMENTED);
    }

    @Override
    public NameAndUuidDto getResourceObjectInternal(UUID objectUuid) throws NotFoundException {
        return cryptoAssetRepository.findResourceObject(objectUuid, CryptographicAssetServiceImpl::displayLabel);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.DETAIL)
    public NameAndUuidDto getResourceObjectExternal(SecuredUUID objectUuid) throws NotFoundException {
        return cryptoAssetRepository
                .findResourceObject(objectUuid.getValue(), CryptographicAssetServiceImpl::displayLabel);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters,
            PaginationRequestDto pagination) {
        return cryptoAssetRepository
                .listResourceObjects(filter, CryptographicAssetServiceImpl::displayLabel, null, pagination);
    }

    // DETAIL is this resource's own object-read gate, and it has no parent resource to chain through.
    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.DETAIL)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        cryptoAssetRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CryptoAsset.class, uuid));
    }

    private static void validatePaging(SearchRequestDto request) {
        // No crypto-asset field is marked sortable, and the contract permits sorting only on fields marked sortable.
        if (request.getSort() != null) {
            throw new ValidationException(
                    "Sorting is not supported for the cryptographic asset inventory; results are ordered by name, then UUID.");
        }
        // PageRequest would otherwise turn an out-of-range value into a 500 rather than a shaped 422.
        if (request.getPageNumber() < 1) {
            throw new ValidationException("Page number must be at least 1, but was " + request.getPageNumber());
        }
        if (request.getItemsPerPage() < 1) {
            throw new ValidationException("Items per page must be at least 1, but was " + request.getItemsPerPage());
        }
        if (request.getItemsPerPage() > MAX_ITEMS_PER_PAGE) {
            request.setItemsPerPage(MAX_ITEMS_PER_PAGE);
        }
    }

    private List<CryptographicAssetDto> loadPage(List<UUID> pageUuids) {
        if (pageUuids.isEmpty()) {
            return List.of();
        }
        Map<UUID, CryptoAssetListRow> rowsByUuid = cryptoAssetRepository
                .findListRowsByUuids(pageUuids)
                .stream()
                .collect(Collectors.toMap(CryptoAssetListRow::uuid, Function.identity()));
        return pageUuids
                .stream()
                // A uuid whose row vanished between the page and projection queries is skipped rather than failed.
                .map(rowsByUuid::get)
                .filter(Objects::nonNull)
                .map(CryptographicAssetServiceImpl::toDto)
                .collect(Collectors.toList());
    }

    /**
     * The role-permissions object picker's display label: the asset's name, or -- for a bare producer row with none --
     * its recorded OID, the next-best stable label. Both are producer-derived display values.
     */
    private static Expression<String> displayLabel(Root<CryptoAsset> root, CriteriaBuilder cb) {
        return cb.coalesce(root.get(CryptoAsset_.name), root.get(CryptoAsset_.oid));
    }

    private static CryptographicAssetDto toDto(CryptoAssetListRow row) {
        CryptographicAssetDto dto = new CryptographicAssetDto();
        dto.setUuid(row.uuid());
        dto.setName(row.name());
        dto.setType(row.assetType());
        dto.setSourceCbomCount(row.sourceCount());
        dto.setOccurrenceCount(row.occurrenceCount());
        // A never-evaluated asset is served as UNKNOWN, the ratified "the rules cannot classify this asset"
        // verdict, because the contract marks the field required.
        dto.setPqcVerdict(row.pqcVerdict() == null ? PqcVerdict.UNKNOWN : row.pqcVerdict());
        // The guard records that a safety rule kept contradicting or ambiguous producer claims apart, which is
        // exactly the contract's "contradicting claims quarantined pending reconciliation".
        dto.setQuarantined(row.identityGuard() != null);
        return dto;
    }
}
