package com.otilm.core.service.impl;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.oid.*;
import com.otilm.api.model.core.oid.properties.CertificateExtensionOidPropertiesDto;
import com.otilm.api.model.core.oid.properties.RdnAttributeTypeOidPropertiesDto;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.core.comparator.SearchFieldDataComparator;
import com.otilm.core.dao.entity.oid.*;
import com.otilm.core.dao.entity.oid.CustomOidEntry;
import com.otilm.core.dao.entity.oid.ExtendedKeyUsageCustomOidEntry;
import com.otilm.core.dao.entity.oid.GenericCustomOidEntry;
import com.otilm.core.dao.entity.oid.RdnAttributeTypeCustomOidEntry;
import com.otilm.core.dao.repository.CustomOidEntryRepository;
import com.otilm.core.mapper.oid.CustomOidEntryMapper;
import com.otilm.core.enums.FilterField;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.oid.OidHandler;
import com.otilm.core.oid.OidRecord;
import com.otilm.core.security.authz.ExternalAuthorization;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.CertificateInternalService;
import com.otilm.core.service.CustomOidEntryExternalService;
import com.otilm.core.util.FilterPredicatesBuilder;
import com.otilm.core.util.RequestValidatorHelper;
import com.otilm.core.util.SearchHelper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.function.TriFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service(Resource.Codes.OID)
@Transactional
public class CustomOidEntryServiceImpl implements CustomOidEntryExternalService {

    public static final String OID_ENTRY = "OID Entry";
    private final CustomOidEntryRepository customOidEntryRepository;
    private CertificateInternalService certificateService;
    /** Rows shadowed by a built-in system OID; see {@link #getShadowedCustomOidEntries}. */
    private volatile Set<String> shadowedCustomOidEntries = Collections.emptySet();

    @Autowired
    public void setCertificateService(CertificateInternalService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public CustomOidEntryServiceImpl(CustomOidEntryRepository oidEntryRepository) {
        this.customOidEntryRepository = oidEntryRepository;

        refreshCache();
    }


    @Scheduled(fixedRateString = "${settings.cache.refresh-interval}", timeUnit = TimeUnit.SECONDS, initialDelayString = "${settings.cache.refresh-interval}")
    public void refreshCache() {
        for (OidCategory oidCategory : OidCategory.values()) {
            OidHandler.cacheOidCategory(oidCategory, getOidToRecordMap(oidCategory));
        }
        publishShadowedCustomOidEntries();
    }

    /**
     * Recomputes the shadowed-row set from one query and publishes it in a single assignment, logging
     * only when the set changes. Derived in one shot rather than accumulated per category, because
     * refreshCache is reachable concurrently from the scheduler and from bulkDeleteCustomOidEntry, and a
     * read-modify-write across categories can drop a category's contribution however volatile the field.
     */
    private void publishShadowedCustomOidEntries() {
        Set<String> shadowed = customOidEntryRepository.findAll().stream()
                .filter(entry -> {
                    SystemOid systemOid = SystemOid.fromOID(entry.getOid());
                    return systemOid != null && systemOid.getCategory() == entry.getCategory();
                })
                .map(CustomOidEntry::getOid)
                .collect(Collectors.toCollection(TreeSet::new));
        if (shadowed.equals(shadowedCustomOidEntries)) {
            return;
        }
        shadowedCustomOidEntries = Collections.unmodifiableSet(shadowed);
        shadowed.forEach(oid -> log.warn(
                "Custom OID entry {} shares its OID with a built-in system OID. The custom entry wins, so the "
                        + "built-in defaults do not apply; delete the custom entry to fall back to them.", oid));
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.CREATE)
    public CustomOidEntryDetailResponseDto createCustomOidEntry(CustomOidEntryRequestDto request) {
        String oid = request.getOid();
        if (SystemOid.fromOID(oid) != null)
            throw new ValidationException("OID %s is reserved for system OID %s.".formatted(oid, SystemOid.fromOID(oid).getDisplayName()));
        if (customOidEntryRepository.existsById(oid))
            throw new ValidationException("OID Entry with OID %s already exists.".formatted(oid));
        CustomOidEntry customOidEntry;

        String code = null;
        List<String> altCodes = null;
        Boolean defaultCritical = null;
        ExtensionValueEncoding valueEncoding = null;

        switch (request.getCategory()) {
            case GENERIC -> customOidEntry = new GenericCustomOidEntry();
            case EXTENDED_KEY_USAGE -> customOidEntry = new ExtendedKeyUsageCustomOidEntry();
            case RDN_ATTRIBUTE_TYPE -> {
                customOidEntry = new RdnAttributeTypeCustomOidEntry();
                if (!(request.getAdditionalProperties() instanceof RdnAttributeTypeOidPropertiesDto additionalProperties))
                    throw new ValidationException("Incorrect type of properties for OID category RDN Attribute type.");
                code = additionalProperties.getCode();
                Set<String> allCodes = getAllCodesInLowerCase();
                if (allCodes.contains(code.toLowerCase()))
                    throw new ValidationException("Code %s is already used".formatted(code));
                ((RdnAttributeTypeCustomOidEntry) customOidEntry).setCode(code);
                certificateService.updateCertificateDNs(oid, code, oid);
                altCodes = additionalProperties.getAltCodes();
                for (String altCode : altCodes) {
                    if (allCodes.contains(altCode.toLowerCase()))
                        throw new ValidationException("Alt Code %s is already used".formatted(altCode));
                }
                ((RdnAttributeTypeCustomOidEntry) customOidEntry).setAltCodes(altCodes);
            }
            case CERTIFICATE_EXTENSION -> {
                customOidEntry = new CertificateExtensionCustomOidEntry();
                if (!(request.getAdditionalProperties() instanceof CertificateExtensionOidPropertiesDto additionalProperties))
                    throw new ValidationException("Incorrect type of properties for OID category Certificate Extension.");
                defaultCritical = additionalProperties.getDefaultCritical();
                valueEncoding = additionalProperties.getValueEncoding();
                ((CertificateExtensionCustomOidEntry) customOidEntry).setDefaultCritical(defaultCritical);
                ((CertificateExtensionCustomOidEntry) customOidEntry).setValueEncoding(valueEncoding);
            }
            default -> throw new ValidationException("Unsupported OID category: " + request.getCategory());
        }

        customOidEntry.setDisplayName(request.getDisplayName());
        customOidEntry.setCategory(request.getCategory());
        customOidEntry.setDescription(request.getDescription());
        customOidEntry.setOid(oid);

        customOidEntryRepository.save(customOidEntry);

        OidHandler.cacheOid(request.getCategory(), oid, OidRecord.builder()
                .displayName(customOidEntry.getDisplayName())
                .code(code)
                .altCodes(altCodes)
                .defaultCritical(defaultCritical)
                .valueEncoding(valueEncoding)
                .build());
        return CustomOidEntryMapper.toDetailDto(customOidEntry);
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.DETAIL)
    public CustomOidEntryDetailResponseDto getCustomOidEntry(String oid) throws NotFoundException {
        CustomOidEntry customOidEntry = customOidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException(OID_ENTRY, oid));
        return CustomOidEntryMapper.toDetailDto(customOidEntry);
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.UPDATE)
    public CustomOidEntryDetailResponseDto editCustomOidEntry(String oid, CustomOidEntryUpdateRequestDto request) throws NotFoundException {
        CustomOidEntry customOidEntry = customOidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException(OID_ENTRY, oid));
        String code = null;
        List<String> altCodes = null;
        Boolean defaultCritical = null;
        ExtensionValueEncoding valueEncoding = null;

        if (customOidEntry instanceof RdnAttributeTypeCustomOidEntry rdnAttributeTypeOidEntry) {
            if (!(request.getAdditionalProperties() instanceof RdnAttributeTypeOidPropertiesDto additionalProperties))
                throw new ValidationException("Incorrect properties for OID category RDN Attribute type.");
            code = additionalProperties.getCode();
            String oldCode = rdnAttributeTypeOidEntry.getCode();
            Set<String> allCodes = getAllCodesInLowerCase();
            if (!oldCode.equals(code)) {
                if (allCodes.contains(code.toLowerCase()))
                    throw new ValidationException("Code %s is already used".formatted(code));
                rdnAttributeTypeOidEntry.setCode(code);
                certificateService.updateCertificateDNs(oid, code, oldCode);
            }

            altCodes = additionalProperties.getAltCodes();
            Set<String> oldAltCodes = rdnAttributeTypeOidEntry.getAltCodes().stream().map(String::toLowerCase).collect(Collectors.toSet());
            for (String altCode : altCodes) {
                if (!oldAltCodes.contains(altCode.toLowerCase()) && allCodes.contains(altCode.toLowerCase()))
                    throw new ValidationException("Alt Code %s is already used".formatted(altCode));
            }
            rdnAttributeTypeOidEntry.setAltCodes(additionalProperties.getAltCodes());
        }
        else if (customOidEntry instanceof CertificateExtensionCustomOidEntry extensionEntry) {
            if (!(request.getAdditionalProperties() instanceof CertificateExtensionOidPropertiesDto additionalProperties))
                throw new ValidationException("Incorrect properties for OID category Certificate Extension.");
            defaultCritical = additionalProperties.getDefaultCritical();
            valueEncoding = additionalProperties.getValueEncoding();
            extensionEntry.setDefaultCritical(defaultCritical);
            extensionEntry.setValueEncoding(valueEncoding);
        }

        customOidEntry.setDisplayName(request.getDisplayName());
        customOidEntry.setDescription(request.getDescription());
        customOidEntryRepository.save(customOidEntry);

        // Cached unconditionally: a custom row wins over a same-OID built-in (see getOidToRecordMap),
        // so skipping this would leave the DB and the registry disagreeing after a successful edit.
        OidHandler.cacheOid(customOidEntry.getCategory(), oid, OidRecord.builder()
                .displayName(customOidEntry.getDisplayName())
                .code(code)
                .altCodes(altCodes)
                .defaultCritical(defaultCritical)
                .valueEncoding(valueEncoding)
                .build());
        return CustomOidEntryMapper.toDetailDto(customOidEntry);
    }

    private static Set<String> getAllCodesInLowerCase() {
        return OidHandler.getOidCache(OidCategory.RDN_ATTRIBUTE_TYPE).values().stream().flatMap(r -> {
                    List<String> combined = new ArrayList<>();
                    combined.add(r.code());
                    combined.addAll(r.altCodes());
                    return combined.stream();
                }).map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.DELETE)
    public void deleteCustomOidEntry(String oid) throws NotFoundException {
        CustomOidEntry customOidEntry = customOidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException(OID_ENTRY, oid));
        customOidEntryRepository.delete(customOidEntry);
        // Deleting a row that shadowed a built-in must hand the OID back to the built-in rather than drop
        // it from the registry, since the custom record was the effective one while it existed.
        SystemOid shadowed = SystemOid.fromOID(oid);
        if (shadowed != null && shadowed.getCategory() == customOidEntry.getCategory()) {
            OidHandler.cacheOid(shadowed.getCategory(), oid, OidRecord.builder()
                    .displayName(shadowed.getDisplayName())
                    .code(shadowed.getCode())
                    .altCodes(shadowed.getAltCodes())
                    .defaultCritical(shadowed.getDefaultCritical())
                    .valueEncoding(shadowed.getValueEncoding())
                    .build());
            publishShadowedCustomOidEntries();
        } else {
            OidHandler.removeCachedOid(customOidEntry.getCategory(), oid);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.DELETE)
    public void bulkDeleteCustomOidEntry(List<String> oids) {
        customOidEntryRepository.deleteAllById(oids);
        refreshCache();
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.LIST)
    public CustomOidEntryListResponseDto listCustomOidEntries(SearchRequestDto request) {
        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<CustomOidEntry>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<CustomOidEntryResponseDto> oidEntries = customOidEntryRepository.findUsingSecurityFilter(SecurityFilter.create(), List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get(CustomOidEntry_.oid)))
                .stream()
                .map(CustomOidEntryMapper::toDto).toList();
        final Long totalItems = customOidEntryRepository.countUsingSecurityFilter(SecurityFilter.create(), additionalWhereClause);
        CustomOidEntryListResponseDto response = new CustomOidEntryListResponseDto();
        response.setOidEntries(oidEntries);
        response.setItemsPerPage(request.getItemsPerPage());
        response.setPageNumber(request.getPageNumber());
        response.setTotalItems(totalItems);
        response.setTotalPages((int) Math.ceil((double) totalItems / request.getItemsPerPage()));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.LIST)
    public List<CustomOidEntryDetailResponseDto> listSystemOidEntries(OidCategory category) {
        return Arrays.stream(SystemOid.values())
                .filter(systemOid -> category == null || systemOid.getCategory() == category)
                .map(CustomOidEntryMapper::toDetailDto)
                .toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = new ArrayList<>();
        List<SearchFieldDataDto> fields = List.of(
                SearchHelper.prepareSearch(FilterField.OID_ENTRY_DISPLAY_NAME),
                SearchHelper.prepareSearch(FilterField.OID_ENTRY_OID),
                SearchHelper.prepareSearch(FilterField.OID_ENTRY_CODE),
                SearchHelper.prepareSearch(FilterField.OID_ENTRY_CATEGORY, Arrays.stream(OidCategory.values()).map(OidCategory::getCode).toList())
        );
        fields = new ArrayList<>(fields);
        fields.sort(new SearchFieldDataComparator());
        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));

        return searchFieldDataByGroupDtos;
    }

    /**
     * OIDs held by a {@code custom_oid_entry} row that a built-in system OID now shadows, so the row's
     * configured properties no longer apply. Exposed so the condition can be surfaced to an operator;
     * the resolution is to delete the row.
     */
    public Set<String> getShadowedCustomOidEntries() {
        return shadowedCustomOidEntries;
    }

    private Map<String, OidRecord> getOidToRecordMap(OidCategory oidCategory) {
        // Cache DB OIDs
        Map<String, OidRecord> oidToDisplayNameMap = new HashMap<>(customOidEntryRepository.findAllByCategory(oidCategory)
                .stream().collect(Collectors.toMap(CustomOidEntry::getOid, oid -> {
                    boolean isRdn = oidCategory == OidCategory.RDN_ATTRIBUTE_TYPE;
                    boolean isExt = oidCategory == OidCategory.CERTIFICATE_EXTENSION;
                    return OidRecord.builder()
                            .displayName(oid.getDisplayName())
                            .code(isRdn ? ((RdnAttributeTypeCustomOidEntry) oid).getCode() : null)
                            .altCodes(isRdn ? ((RdnAttributeTypeCustomOidEntry) oid).getAltCodes() : null)
                            .defaultCritical(isExt ? ((CertificateExtensionCustomOidEntry) oid).getDefaultCritical() : null)
                            .valueEncoding(isExt ? ((CertificateExtensionCustomOidEntry) oid).getValueEncoding() : null)
                            .build();
                })));
        // Cache System OIDs. defaultCritical and valueEncoding must be carried through: the projector
        // reads them from this cache, and an unset defaultCritical silently emits a critical extension
        // such as Name Constraints as non-critical.
        //
        // putIfAbsent, not putAll: a custom row for the same OID predates the promotion, and overwriting
        // it would drop the operator's code, alt codes, criticality and encoding wholesale — taking a
        // code out of the registry entirely, so every DN carrying it fails to resolve at request time.
        // The operator's record therefore wins and is reported as shadowed, matching how a contested
        // code resolves. The built-in stays reachable by its dotted OID.
        Arrays.stream(SystemOid.values())
                .filter(oid -> oid.getCategory() == oidCategory)
                .forEach(oid -> oidToDisplayNameMap.putIfAbsent(oid.getOid(), OidRecord.builder()
                        .displayName(oid.getDisplayName())
                        .code(oid.getCode())
                        .altCodes(oid.getAltCodes())
                        .defaultCritical(oid.getDefaultCritical())
                        .valueEncoding(oid.getValueEncoding())
                        .build()));
        return oidToDisplayNameMap;
    }
}
