package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.CustomOidEntryController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.oid.*;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.service.CustomOidEntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class CustomOidEntryControllerImpl implements CustomOidEntryController {

    private CustomOidEntryService customOidEntryService;

    @Autowired
    public void setOidEntryService(CustomOidEntryService customOidEntryService) {
        this.customOidEntryService = customOidEntryService;
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.CREATE)
    public CustomOidEntryDetailResponseDto createCustomOidEntry(CustomOidEntryRequestDto requestDto) {
        return customOidEntryService.createCustomOidEntry(requestDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.DETAIL)
    public CustomOidEntryDetailResponseDto getCustomOidEntry(String oid) throws NotFoundException {
        return customOidEntryService.getCustomOidEntry(oid);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.UPDATE)
    public CustomOidEntryDetailResponseDto editCustomOidEntry(String oid, CustomOidEntryUpdateRequestDto updateDto) throws NotFoundException {
        return customOidEntryService.editCustomOidEntry(oid, updateDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.DELETE)
    public void deleteCustomOidEntry(String oid) throws NotFoundException {
        customOidEntryService.deleteCustomOidEntry(oid);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.DELETE)
    public void bulkDeleteCustomOidEntry(List<String> oids) {
        customOidEntryService.bulkDeleteCustomOidEntry(oids);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.LIST)
    public CustomOidEntryListResponseDto listCustomOidEntries(SearchRequestDto searchRequestDto) {
        return customOidEntryService.listCustomOidEntries(searchRequestDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SEARCH_FILTER, affiliatedResource = Resource.OID, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableInformation() {
        return customOidEntryService.getSearchableFieldInformation();
    }
}
