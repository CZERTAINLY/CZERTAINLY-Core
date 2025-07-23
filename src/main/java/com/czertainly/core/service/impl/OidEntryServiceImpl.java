package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.oid.*;
import com.czertainly.core.dao.entity.oid.OidEntry;
import com.czertainly.core.dao.entity.oid.OidEntry_;
import com.czertainly.core.dao.entity.oid.RdnAttributeTypeOidEntry;
import com.czertainly.core.dao.repository.OidEntryRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.OidEntryService;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.RequestValidatorHelper;
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

import java.util.List;

@Service(Resource.Codes.OID)
@Transactional
public class OidEntryServiceImpl implements OidEntryService {

    private OidEntryRepository oidEntryRepository;

    @Autowired
    public void setOidEntryRepository(OidEntryRepository oidEntryRepository) {
        this.oidEntryRepository = oidEntryRepository;
    }

    @Override
    public OidEntryDetailResponseDto createOidEntry(OidEntryRequestDto request) {
        String oid = request.getOid();
        if (oidEntryRepository.existsById(oid)) throw new ValidationException("OID Entry with OID %s already exists.".formatted(oid));
        OidEntry oidEntry;
        AdditionalOidPropertiesDto responseAdditionalProperties = null;

        if (request.getCategory() == OidCategory.RDN_ATTRIBUTE_TYPE) {
            oidEntry = new RdnAttributeTypeOidEntry();
            if (!(request.getAdditionalProperties() instanceof RdnAttributeTypeOidPropertiesDto additionalProperties)) throw new ValidationException("Incorrect properties for OID category RDN Attribute type.");
            if (additionalProperties.getCode() == null) throw new ValidationException("Code must not be empty for OID with category RDN Attribute type.");
            ((RdnAttributeTypeOidEntry) oidEntry).setCode(additionalProperties.getCode());
            ((RdnAttributeTypeOidEntry) oidEntry).setAltCodes(additionalProperties.getAltCodes());
            ((RdnAttributeTypeOidEntry) oidEntry).setValueType(additionalProperties.getValueType());
            responseAdditionalProperties = ((RdnAttributeTypeOidEntry) oidEntry).mapToPropertiesDto();
        } else {
            oidEntry = new OidEntry();
        }

        oidEntry.setDisplayName(request.getDisplayName());
        oidEntry.setCategory(request.getCategory());
        oidEntry.setOid(oid);

        oidEntryRepository.save(oidEntry);

        OidEntryDetailResponseDto response = new OidEntryDetailResponseDto();
        response.setOid(oid);
        response.setCategory(request.getCategory());
        response.setDescription(request.getDescription());
        response.setDisplayName(request.getDisplayName());
        response.setAdditionalProperties(responseAdditionalProperties);
        return response;
    }

    @Override
    public OidEntryDetailResponseDto getOidEntry(String oid) throws NotFoundException {
        OidEntry oidEntry = oidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException("OID Entry", oid));
        OidEntryDetailResponseDto response = oidEntry.mapToDetailDto();
        if (oidEntry.getCategory() == OidCategory.RDN_ATTRIBUTE_TYPE) response.setAdditionalProperties(((RdnAttributeTypeOidEntry) oidEntry).mapToPropertiesDto());
        return response;
    }

    @Override
    public OidEntryResponseDto editOidEntry(String oid, OidEntryUpdateRequestDto request) throws NotFoundException {
        OidEntry oidEntry = oidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException("OID Entry", oid));
        AdditionalOidPropertiesDto responseAdditionalProperties = null;

        if (request.getCategory() == OidCategory.RDN_ATTRIBUTE_TYPE) {
            if (!(request.getAdditionalProperties() instanceof RdnAttributeTypeOidPropertiesDto additionalProperties)) throw new ValidationException("Incorrect properties for OID category RDN Attribute type.");
            if (additionalProperties.getCode() == null) throw new ValidationException("Code must not be empty for OID with category RDN Attribute type.");
            ((RdnAttributeTypeOidEntry) oidEntry).setCode(additionalProperties.getCode());
            ((RdnAttributeTypeOidEntry) oidEntry).setAltCodes(additionalProperties.getAltCodes());
            ((RdnAttributeTypeOidEntry) oidEntry).setValueType(additionalProperties.getValueType());
            responseAdditionalProperties = ((RdnAttributeTypeOidEntry) oidEntry).mapToPropertiesDto();
        } else {
            oidEntry = new OidEntry();
        }

        oidEntry.setDisplayName(request.getDisplayName());
        oidEntry.setCategory(request.getCategory());

        oidEntryRepository.save(oidEntry);

        OidEntryDetailResponseDto response = oidEntry.mapToDetailDto();
        response.setAdditionalProperties(responseAdditionalProperties);
        return response;
    }

    @Override
    public void deleteOidEntry(String oid) throws NotFoundException {
        OidEntry oidEntry = oidEntryRepository.findById(oid).orElseThrow(() -> new NotFoundException("OID Entry", oid));
        oidEntryRepository.delete(oidEntry);
    }

    @Override
    public void bulkDeleteOidEntry(List<String> oids) {
        oidEntryRepository.deleteAllById(oids);
    }

    @Override
    public OidEntryListResponseDto listOidEntries(SearchRequestDto request) {
        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<OidEntry>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<OidEntryResponseDto> oidEntries = oidEntryRepository.findUsingSecurityFilter(SecurityFilter.create(), List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get(OidEntry_.oid)))
                .stream()
                .map(OidEntry::mapToDto).toList();
        final Long totalItems = oidEntryRepository.countUsingSecurityFilter(SecurityFilter.create(), additionalWhereClause);
        OidEntryListResponseDto response = new OidEntryListResponseDto();
        response.setOidEntries(oidEntries);
        response.setItemsPerPage(request.getItemsPerPage());
        response.setPageNumber(request.getPageNumber());
        response.setTotalItems(totalItems);
        response.setTotalPages((int) Math.ceil((double) totalItems / request.getItemsPerPage()));

        return response;
    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {

    }
}
