package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.OidEntryController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.oid.OidEntryListResponseDto;
import com.czertainly.api.model.core.oid.OidEntryRequestDto;
import com.czertainly.api.model.core.oid.OidEntryResponseDto;
import com.czertainly.api.model.core.oid.OidEntryUpdateRequestDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.service.OidEntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class OidEntryControllerImpl implements OidEntryController {

    private OidEntryService oidEntryService;

    @Autowired
    public void setOidEntryService(OidEntryService oidEntryService) {
        this.oidEntryService = oidEntryService;
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.CREATE)
    public OidEntryResponseDto createOidEntry(OidEntryRequestDto requestDto) {
        return oidEntryService.createOidEntry(requestDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.DETAIL)
    public OidEntryResponseDto getOidEntry(String oid) throws NotFoundException {
        return oidEntryService.getOidEntry(oid);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.UPDATE)
    public OidEntryResponseDto editOidEntry(String oid, OidEntryUpdateRequestDto updateDto) throws NotFoundException {
        return oidEntryService.editOidEntry(oid, updateDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.DELETE)
    public void deleteOidEntry(String oid) throws NotFoundException {
        oidEntryService.deleteOidEntry(oid);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.OID, operation = Operation.DELETE)
    public void bulkDeleteOidEntry(List<String> oids) {
        oidEntryService.bulkDeleteOidEntry(oids);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.NOTIFICATION_PROFILE, operation = Operation.LIST)
    public OidEntryListResponseDto listOidEntries(SearchRequestDto searchRequestDto) {
        return oidEntryService.listOidEntries(searchRequestDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SEARCH_FILTER, affiliatedResource = Resource.OID, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableInformation() {
        return List.of();
    }
}
