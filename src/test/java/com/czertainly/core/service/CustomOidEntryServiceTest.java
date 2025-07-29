package com.czertainly.core.service;


import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.oid.*;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.oid.CustomOidEntry;
import com.czertainly.core.dao.entity.oid.GenericCustomOidEntry;
import com.czertainly.core.dao.entity.oid.RdnAttributeTypeCustomOidEntry;
import com.czertainly.core.dao.repository.CustomOidEntryRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

class CustomOidEntryServiceTest extends BaseSpringBootTest {

    public static final String NON_EXISTENT_OID = "1.2";
    @Autowired
    CustomOidEntryService customOidEntryService;

    @Autowired
    CustomOidEntryRepository customOidEntryRepository;

    private CustomOidEntry genericCustomOidEntry;
    private RdnAttributeTypeCustomOidEntry rdnOidEntry;

    @BeforeEach
    void setUp() {
        genericCustomOidEntry = new GenericCustomOidEntry();
        genericCustomOidEntry.setCategory(OidCategory.GENERIC);
        genericCustomOidEntry.setDescription("desc");
        genericCustomOidEntry.setDisplayName("generic");
        genericCustomOidEntry.setOid("1.2.3.4.5");
        customOidEntryRepository.save(genericCustomOidEntry);

        rdnOidEntry = new RdnAttributeTypeCustomOidEntry();
        rdnOidEntry.setCategory(OidCategory.RDN_ATTRIBUTE_TYPE);
        rdnOidEntry.setDescription("desc");
        rdnOidEntry.setDisplayName("rdn");
        rdnOidEntry.setOid("1.2.3.4.6");
        rdnOidEntry.setCode("RDN");
        rdnOidEntry.setAltCodes(List.of("R","D"));
        customOidEntryRepository.save(rdnOidEntry);
    }

    @Test
    void testCreateCustomOidEntry() {
        CustomOidEntryRequestDto request = new CustomOidEntryRequestDto();
        request.setOid("1.2.3");
        request.setCategory(OidCategory.GENERIC);
        request.setDescription("desc");
        request.setDisplayName("display name");
        CustomOidEntryDetailResponseDto response = customOidEntryService.createCustomOidEntry(request);
        Assertions.assertEquals(request.getOid(), response.getOid());
        Assertions.assertEquals(request.getCategory(), response.getCategory());
        Assertions.assertEquals(request.getAdditionalProperties(), response.getAdditionalProperties());
        Assertions.assertEquals(request.getDescription(), response.getDescription());
        Assertions.assertEquals(request.getDisplayName(), response.getDisplayName());
        CustomOidEntry customOidEntry = customOidEntryRepository.findById(request.getOid()).orElse(null);
        Assertions.assertNotNull(customOidEntry);
        Assertions.assertThrows(ValidationException.class, () -> customOidEntryService.createCustomOidEntry(request));
        request.setOid(SystemOid.COUNTRY.getOid());
        Assertions.assertThrows(ValidationException.class, () -> customOidEntryService.createCustomOidEntry(request));

        request.setOid("1.2.3.4");
        request.setCategory(OidCategory.RDN_ATTRIBUTE_TYPE);
        Assertions.assertThrows(ValidationException.class, () -> customOidEntryService.createCustomOidEntry(request));
        RdnAttributeTypeOidPropertiesDto propertiesDto = new RdnAttributeTypeOidPropertiesDto();
        request.setAdditionalProperties(propertiesDto);
        propertiesDto.setCode("CN");
        propertiesDto.setAltCodes(List.of("CN1","CN2"));
        request.setAdditionalProperties(propertiesDto);
        response = customOidEntryService.createCustomOidEntry(request);
        Assertions.assertEquals(request.getOid(), response.getOid());
        Assertions.assertEquals(request.getCategory(), response.getCategory());
        Assertions.assertEquals(request.getAdditionalProperties(), response.getAdditionalProperties());
        Assertions.assertEquals(request.getDescription(), response.getDescription());
        Assertions.assertEquals(request.getDisplayName(), response.getDisplayName());
        Assertions.assertTrue(customOidEntryRepository.existsById(request.getOid()));
        Assertions.assertEquals(propertiesDto.getCode(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getCode());
        Assertions.assertEquals(propertiesDto.getAltCodes(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getAltCodes());
    }

    @Test
    void testGetCustomOidEntry() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> customOidEntryService.getCustomOidEntry(NON_EXISTENT_OID));
        CustomOidEntryDetailResponseDto response = customOidEntryService.getCustomOidEntry(genericCustomOidEntry.getOid());
        Assertions.assertEquals(genericCustomOidEntry.getOid(), response.getOid());
        Assertions.assertEquals(genericCustomOidEntry.getCategory(), response.getCategory());
        Assertions.assertNull(response.getAdditionalProperties());
        Assertions.assertEquals(genericCustomOidEntry.getDescription(), response.getDescription());
        Assertions.assertEquals(genericCustomOidEntry.getDisplayName(), response.getDisplayName());


        response = customOidEntryService.getCustomOidEntry(rdnOidEntry.getOid());
        Assertions.assertEquals(rdnOidEntry.getOid(), response.getOid());
        Assertions.assertEquals(rdnOidEntry.getCategory(), response.getCategory());
        Assertions.assertEquals(rdnOidEntry.getDescription(), response.getDescription());
        Assertions.assertEquals(rdnOidEntry.getDisplayName(), response.getDisplayName());
        Assertions.assertEquals(rdnOidEntry.getCode(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getCode());
        Assertions.assertEquals(rdnOidEntry.getAltCodes(), ((RdnAttributeTypeOidPropertiesDto) response.getAdditionalProperties()).getAltCodes());
    }

    @Test
    void testRemoveOidEntry() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> customOidEntryService.deleteCustomOidEntry(NON_EXISTENT_OID));
        customOidEntryService.deleteCustomOidEntry(genericCustomOidEntry.getOid());
        Assertions.assertTrue(customOidEntryRepository.findById(genericCustomOidEntry.getOid()).isEmpty());
    }

    @Test
    void testBulkDeleteOidEntries() {
        customOidEntryService.bulkDeleteCustomOidEntry(List.of(NON_EXISTENT_OID, genericCustomOidEntry.getOid(), rdnOidEntry.getOid()));
        Assertions.assertTrue(customOidEntryRepository.findAll().isEmpty());
    }

    @Test
    void testListCustomOidEntries() {
        CustomOidEntryListResponseDto response = customOidEntryService.listCustomOidEntries(new SearchRequestDto());
        Assertions.assertEquals(2, response.getOidEntries().size());

        SearchRequestDto searchRequestDto = new SearchRequestDto();
        SearchFilterRequestDto filterRequestDto = new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OID_ENTRY_CATEGORY.name(), FilterConditionOperator.EQUALS, OidCategory.GENERIC.getCode());
        searchRequestDto.setFilters(List.of(filterRequestDto));
        response = customOidEntryService.listCustomOidEntries(searchRequestDto);
        Assertions.assertEquals(1, response.getOidEntries().size());
        Assertions.assertEquals(genericCustomOidEntry.getOid(), response.getOidEntries().getFirst().getOid());

        filterRequestDto = new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OID_ENTRY_CODE.name(), FilterConditionOperator.EQUALS, rdnOidEntry.getCode());
        searchRequestDto.setFilters(List.of(filterRequestDto));
        response = customOidEntryService.listCustomOidEntries(searchRequestDto);
        Assertions.assertEquals(1, response.getOidEntries().size());
        Assertions.assertEquals(rdnOidEntry.getOid(), response.getOidEntries().getFirst().getOid());
    }

    @Test
    void testUpdateOidEntry() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> customOidEntryService.editCustomOidEntry(NON_EXISTENT_OID, new CustomOidEntryUpdateRequestDto()));
        CustomOidEntryUpdateRequestDto request = new CustomOidEntryUpdateRequestDto();
        request.setDisplayName("generic2");
        request.setDescription("newDesc");
        RdnAttributeTypeOidPropertiesDto propertiesDto = new RdnAttributeTypeOidPropertiesDto();
        propertiesDto.setCode("G");
        request.setAdditionalProperties(propertiesDto);
        customOidEntryService.editCustomOidEntry(genericCustomOidEntry.getOid(), request);
        genericCustomOidEntry = customOidEntryRepository.findById(genericCustomOidEntry.getOid()).get();
        Assertions.assertEquals(request.getDisplayName(), genericCustomOidEntry.getDisplayName());
        Assertions.assertEquals(request.getDescription(), genericCustomOidEntry.getDescription());

        customOidEntryService.editCustomOidEntry(rdnOidEntry.getOid(), request);
        rdnOidEntry = (RdnAttributeTypeCustomOidEntry) customOidEntryRepository.findById(rdnOidEntry.getOid()).get();
        Assertions.assertEquals(request.getDisplayName(), rdnOidEntry.getDisplayName());
        Assertions.assertEquals(request.getDescription(), rdnOidEntry.getDescription());
        Assertions.assertEquals(propertiesDto.getCode(), rdnOidEntry.getCode());

    }


}
