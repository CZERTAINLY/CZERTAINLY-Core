package com.czertainly.core.service;


import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.oid.*;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.oid.OidEntry;
import com.czertainly.core.dao.entity.oid.RdnAttributeTypeOidEntry;
import com.czertainly.core.dao.repository.OidEntryRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.List;

class OidEntryServiceTest extends BaseSpringBootTest {

    public static final String NON_EXISTENT_OID = "1.2";
    @Autowired
    OidEntryService oidEntryService;

    @Autowired
    OidEntryRepository oidEntryRepository;

    private OidEntry genericOidEntry;
    private RdnAttributeTypeOidEntry rdnOidEntry;

    @BeforeEach
    void setUp() {
        genericOidEntry = new OidEntry();
        genericOidEntry.setCategory(OidCategory.GENERIC);
        genericOidEntry.setDescription("desc");
        genericOidEntry.setDisplayName("generic");
        genericOidEntry.setOid("1.2.3.4.5");
        oidEntryRepository.save(genericOidEntry);

        rdnOidEntry = new RdnAttributeTypeOidEntry();
        rdnOidEntry.setCategory(OidCategory.RDN_ATTRIBUTE_TYPE);
        rdnOidEntry.setDescription("desc");
        rdnOidEntry.setDisplayName("rdn");
        rdnOidEntry.setOid("1.2.3.4.6");
        rdnOidEntry.setCode("RDN");
        rdnOidEntry.setAltCodes(List.of("R","D"));
        rdnOidEntry.setValueType("STRING");
        oidEntryRepository.save(rdnOidEntry);
    }

    @Test
    void testCreateOidEntry() {
        OidEntryRequestDto request = new OidEntryRequestDto();
        request.setOid("1.2.3");
        request.setCategory(OidCategory.GENERIC);
        request.setDescription("desc");
        request.setDisplayName("display name");
        OidEntryDetailResponseDto response = oidEntryService.createOidEntry(request);
        Assertions.assertEquals(request.getOid(), response.getOid());
        Assertions.assertEquals(request.getCategory(), response.getCategory());
        Assertions.assertEquals(request.getAdditionalProperties(), response.getAdditionalProperties());
        Assertions.assertEquals(request.getDescription(), response.getDescription());
        Assertions.assertEquals(request.getDisplayName(), response.getDisplayName());
        OidEntry oidEntry = oidEntryRepository.findById(request.getOid()).orElse(null);
        Assertions.assertNotNull(oidEntry);
        Assertions.assertThrows(ValidationException.class, () -> oidEntryService.createOidEntry(request));
        request.setOid(SystemOid.COUNTRY.getOid());
        Assertions.assertThrows(ValidationException.class, () -> oidEntryService.createOidEntry(request));

        request.setOid("1.2.3.4");
        request.setCategory(OidCategory.RDN_ATTRIBUTE_TYPE);
        Assertions.assertThrows(ValidationException.class, () -> oidEntryService.createOidEntry(request));
        RdnAttributeTypeOidPropertiesDto propertiesDto = new RdnAttributeTypeOidPropertiesDto();
        request.setAdditionalProperties(propertiesDto);
        Assertions.assertThrows(ValidationException.class, () -> oidEntryService.createOidEntry(request));
        propertiesDto.setCode("CN");
        propertiesDto.setAltCodes(List.of("CN1","CN2"));
        propertiesDto.setValueType("DER STRING");
        request.setAdditionalProperties(propertiesDto);
        response = oidEntryService.createOidEntry(request);
        Assertions.assertEquals(request.getOid(), response.getOid());
        Assertions.assertEquals(request.getCategory(), response.getCategory());
        Assertions.assertEquals(request.getAdditionalProperties(), response.getAdditionalProperties());
        Assertions.assertEquals(request.getDescription(), response.getDescription());
        Assertions.assertEquals(request.getDisplayName(), response.getDisplayName());
        Assertions.assertTrue(oidEntryRepository.existsById(request.getOid()));
        Assertions.assertEquals(propertiesDto.getCode(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getCode());
        Assertions.assertEquals(propertiesDto.getAltCodes(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getAltCodes());
        Assertions.assertEquals(propertiesDto.getValueType(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getValueType());
    }

    @Test
    void testGetOidEntry() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> oidEntryService.getOidEntry(NON_EXISTENT_OID));
        OidEntryDetailResponseDto response = oidEntryService.getOidEntry(genericOidEntry.getOid());
        Assertions.assertEquals(genericOidEntry.getOid(), response.getOid());
        Assertions.assertEquals(genericOidEntry.getCategory(), response.getCategory());
        Assertions.assertNull(response.getAdditionalProperties());
        Assertions.assertEquals(genericOidEntry.getDescription(), response.getDescription());
        Assertions.assertEquals(genericOidEntry.getDisplayName(), response.getDisplayName());


        response = oidEntryService.getOidEntry(rdnOidEntry.getOid());
        Assertions.assertEquals(rdnOidEntry.getOid(), response.getOid());
        Assertions.assertEquals(rdnOidEntry.getCategory(), response.getCategory());
        Assertions.assertEquals(rdnOidEntry.getDescription(), response.getDescription());
        Assertions.assertEquals(rdnOidEntry.getDisplayName(), response.getDisplayName());
        Assertions.assertEquals(rdnOidEntry.getCode(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getCode());
        Assertions.assertEquals(rdnOidEntry.getAltCodes(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getAltCodes());
        Assertions.assertEquals(rdnOidEntry.getValueType(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getValueType());
    }

    @Test
    void testRemoveOidEntry() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> oidEntryService.deleteOidEntry(NON_EXISTENT_OID));
        oidEntryService.deleteOidEntry(genericOidEntry.getOid());
        Assertions.assertTrue(oidEntryRepository.findById(genericOidEntry.getOid()).isEmpty());
    }

    @Test
    void testBulkDeleteOidEntries() {
        oidEntryService.bulkDeleteOidEntry(List.of(NON_EXISTENT_OID, genericOidEntry.getOid(), rdnOidEntry.getOid()));
        Assertions.assertTrue(oidEntryRepository.findAll().isEmpty());
    }

    @Test
    void testListOidEntries() {
        OidEntryListResponseDto response = oidEntryService.listOidEntries(new SearchRequestDto());
        Assertions.assertEquals(2, response.getOidEntries().size());

        SearchRequestDto searchRequestDto = new SearchRequestDto();
        SearchFilterRequestDto filterRequestDto = new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OID_ENTRY_CATEGORY.name(), FilterConditionOperator.EQUALS, OidCategory.GENERIC.getCode());
        searchRequestDto.setFilters(List.of(filterRequestDto));
        response = oidEntryService.listOidEntries(searchRequestDto);
        Assertions.assertEquals(1, response.getOidEntries().size());
        Assertions.assertEquals(genericOidEntry.getOid(), response.getOidEntries().getFirst().getOid());

        filterRequestDto = new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OID_ENTRY_CODE.name(), FilterConditionOperator.EQUALS, rdnOidEntry.getCode());
        searchRequestDto.setFilters(List.of(filterRequestDto, new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OID_ENTRY_ALT_CODES.name(), FilterConditionOperator.EQUALS, (Serializable) rdnOidEntry.getAltCodes())));
        response = oidEntryService.listOidEntries(searchRequestDto);
        Assertions.assertEquals(1, response.getOidEntries().size());
        Assertions.assertEquals(rdnOidEntry.getOid(), response.getOidEntries().getFirst().getOid());
    }

    @Test
    void testUpdateOidEntry() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> oidEntryService.editOidEntry(NON_EXISTENT_OID, new OidEntryUpdateRequestDto()));
        OidEntryUpdateRequestDto request = new OidEntryUpdateRequestDto();
        request.setDisplayName("generic2");
        request.setCategory(OidCategory.RDN_ATTRIBUTE_TYPE);
        RdnAttributeTypeOidPropertiesDto propertiesDto = new RdnAttributeTypeOidPropertiesDto();
        propertiesDto.setCode("G");
        request.setAdditionalProperties(propertiesDto);
        oidEntryService.editOidEntry(genericOidEntry.getOid(), request);
        genericOidEntry = oidEntryRepository.findById(genericOidEntry.getOid()).get();

    }


}
