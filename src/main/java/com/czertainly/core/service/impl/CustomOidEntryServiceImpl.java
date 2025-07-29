package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.oid.*;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.oid.*;
import com.czertainly.core.dao.repository.CustomOidEntryRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CustomOidEntryService;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.RequestValidatorHelper;
import com.czertainly.core.util.SearchHelper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service(Resource.Codes.OID)
@Transactional
public class CustomOidEntryServiceImpl implements CustomOidEntryService {

    public static final String OID_ENTRY = "OID Entry";
    private CustomOidEntryRepository customOidEntryRepository;

    @Autowired
    public void setOidEntryRepository(CustomOidEntryRepository customOidEntryRepository) {
        this.customOidEntryRepository = customOidEntryRepository;
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
        AdditionalOidPropertiesDto responseAdditionalProperties = null;

        switch (request.getCategory()) {
            case GENERIC -> customOidEntry = new GenericCustomOidEntry();
            case EXTENDED_KEY_USAGE -> customOidEntry = new ExtendedKeyUsageCustomOidEntry();
            case RDN_ATTRIBUTE_TYPE -> {
                customOidEntry = new RdnAttributeTypeCustomOidEntry();
                if (!(request.getAdditionalProperties() instanceof RdnAttributeTypeOidPropertiesDto additionalProperties))
                    throw new ValidationException("Incorrect type of properties for OID category RDN Attribute type.");
                ((RdnAttributeTypeCustomOidEntry) customOidEntry).setCode(additionalProperties.getCode());
                ((RdnAttributeTypeCustomOidEntry) customOidEntry).setAltCodes(additionalProperties.getAltCodes());
                responseAdditionalProperties = ((RdnAttributeTypeCustomOidEntry) customOidEntry).mapToPropertiesDto();
            }
            default -> customOidEntry = new CustomOidEntry();
        }

        customOidEntry.setDisplayName(request.getDisplayName());
        customOidEntry.setCategory(request.getCategory());
        customOidEntry.setDescription(request.getDescription());
        customOidEntry.setOid(oid);

        customOidEntryRepository.save(customOidEntry);

        CustomOidEntryDetailResponseDto response = customOidEntry.mapToDetailDto();
        response.setAdditionalProperties(responseAdditionalProperties);
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.DETAIL)
    public CustomOidEntryDetailResponseDto getCustomOidEntry(String oid) throws NotFoundException {
        CustomOidEntry customOidEntry = customOidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException(OID_ENTRY, oid));
        CustomOidEntryDetailResponseDto response = customOidEntry.mapToDetailDto();
        if (customOidEntry.getCategory() == OidCategory.RDN_ATTRIBUTE_TYPE)
            response.setAdditionalProperties(((RdnAttributeTypeCustomOidEntry) customOidEntry).mapToPropertiesDto());
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.UPDATE)
    public CustomOidEntryDetailResponseDto editCustomOidEntry(String oid, CustomOidEntryUpdateRequestDto request) throws NotFoundException {
        CustomOidEntry customOidEntry = customOidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException(OID_ENTRY, oid));
        AdditionalOidPropertiesDto responseAdditionalProperties = null;

        if (customOidEntry instanceof RdnAttributeTypeCustomOidEntry rdnAttributeTypeOidEntry) {
            if (!(request.getAdditionalProperties() instanceof RdnAttributeTypeOidPropertiesDto additionalProperties))
                throw new ValidationException("Incorrect properties for OID category RDN Attribute type.");
            rdnAttributeTypeOidEntry.setCode(additionalProperties.getCode());
            rdnAttributeTypeOidEntry.setAltCodes(additionalProperties.getAltCodes());
            responseAdditionalProperties = rdnAttributeTypeOidEntry.mapToPropertiesDto();
        }

        customOidEntry.setDisplayName(request.getDisplayName());
        customOidEntry.setDescription(request.getDescription());
        customOidEntryRepository.save(customOidEntry);

        CustomOidEntryDetailResponseDto response = customOidEntry.mapToDetailDto();
        response.setAdditionalProperties(responseAdditionalProperties);
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.DELETE)
    public void deleteCustomOidEntry(String oid) throws NotFoundException {
        CustomOidEntry customOidEntry = customOidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException(OID_ENTRY, oid));
        customOidEntryRepository.delete(customOidEntry);
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.DELETE)
    public void bulkDeleteCustomOidEntry(List<String> oids) {
        customOidEntryRepository.deleteAllById(oids);
    }

    @Override
    @ExternalAuthorization(resource = Resource.OID, action = ResourceAction.LIST)
    public CustomOidEntryListResponseDto listCustomOidEntries(SearchRequestDto request) {
        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<CustomOidEntry>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<CustomOidEntryResponseDto> oidEntries = customOidEntryRepository.findUsingSecurityFilter(SecurityFilter.create(), List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get(CustomOidEntry_.oid)))
                .stream()
                .map(CustomOidEntry::mapToDto).toList();
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

    @Override
    public Map<String, String> getOidToDisplayNameMap(OidCategory oidCategory) {
        Map<String, String> oidToDisplayNameMap = new HashMap<>(SystemOid.getMapOfOidToDisplayName(oidCategory));
        oidToDisplayNameMap.putAll(customOidEntryRepository.findAllByCategory(oidCategory)
                .stream().collect(Collectors.toMap(CustomOidEntry::getOid, CustomOidEntry::getDisplayName)));
        return oidToDisplayNameMap;
    }

    @Override
    public Map<String, String> getOidToCodeMap() {
        Map<String, String> oidToCodeMap = new HashMap<>(SystemOid.getMapOfOidToCode());
        oidToCodeMap.putAll(customOidEntryRepository.findAllByCategory(OidCategory.RDN_ATTRIBUTE_TYPE)
                .stream().collect(Collectors.toMap(CustomOidEntry::getOid, oidEntry ->
                        ((RdnAttributeTypeCustomOidEntry)oidEntry).getCode())));
        return oidToCodeMap;
    }
}
