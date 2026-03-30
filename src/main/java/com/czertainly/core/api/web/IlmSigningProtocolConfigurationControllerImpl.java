package com.czertainly.core.api.web;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.IlmSigningProtocolConfigurationController;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.IlmSigningProtocolConfigurationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class IlmSigningProtocolConfigurationControllerImpl implements IlmSigningProtocolConfigurationController {

    private final IlmSigningProtocolConfigurationService ilmSigningProtocolConfigurationService;

    @Autowired
    public IlmSigningProtocolConfigurationControllerImpl(IlmSigningProtocolConfigurationService ilmSigningProtocolConfigurationService) {
        this.ilmSigningProtocolConfigurationService = ilmSigningProtocolConfigurationService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION)
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return ilmSigningProtocolConfigurationService.getSearchableFieldInformation();
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.LIST)
    public PaginationResponseDto<IlmSigningProtocolConfigurationListDto> listIlmSigningProtocolConfigurations(SearchRequestDto request) {
        return ilmSigningProtocolConfigurationService.listIlmSigningProtocolConfigurations(request, SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.DETAIL)
    public IlmSigningProtocolConfigurationDto getIlmSigningProtocolConfiguration(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        return ilmSigningProtocolConfigurationService.getIlmSigningProtocolConfiguration(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.CREATE)
    public IlmSigningProtocolConfigurationDto createIlmSigningProtocolConfiguration(@Valid IlmSigningProtocolConfigurationRequestDto request) throws AttributeException, NotFoundException {
        return ilmSigningProtocolConfigurationService.createIlmSigningProtocolConfiguration(request);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.UPDATE)
    public IlmSigningProtocolConfigurationDto updateIlmSigningProtocolConfiguration(@LogResource(uuid = true) UUID uuid, IlmSigningProtocolConfigurationRequestDto request) throws NotFoundException, AttributeException {
        return ilmSigningProtocolConfigurationService.updateIlmSigningProtocolConfiguration(SecuredUUID.fromUUID(uuid), request);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.DELETE)
    public void deleteIlmSigningProtocolConfiguration(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        ilmSigningProtocolConfigurationService.deleteIlmSigningProtocolConfiguration(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.DELETE)
    public List<BulkActionMessageDto> bulkDeleteIlmSigningProtocolConfigurations(@LogResource(uuid = true) List<UUID> uuids) {
        return ilmSigningProtocolConfigurationService.bulkDeleteIlmSigningProtocolConfigurations(SecuredUUID.fromUuidList(uuids));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.ENABLE)
    public void enableIlmSigningProtocolConfiguration(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        ilmSigningProtocolConfigurationService.enableIlmSigningProtocolConfiguration(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.ENABLE)
    public List<BulkActionMessageDto> bulkEnableIlmSigningProtocolConfigurations(@LogResource(uuid = true) List<UUID> uuids) {
        return ilmSigningProtocolConfigurationService.bulkEnableIlmSigningProtocolConfigurations(SecuredUUID.fromUuidList(uuids));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.DISABLE)
    public void disableIlmSigningProtocolConfiguration(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        ilmSigningProtocolConfigurationService.disableIlmSigningProtocolConfiguration(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, operation = Operation.DISABLE)
    public List<BulkActionMessageDto> bulkDisableIlmSigningProtocolConfigurations(@LogResource(uuid = true) List<UUID> uuids) {
        return ilmSigningProtocolConfigurationService.bulkDisableIlmSigningProtocolConfigurations(SecuredUUID.fromUuidList(uuids));
    }
}
