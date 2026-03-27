package com.czertainly.core.api.web;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.TspConfigurationController;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationRequestDto;
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
import com.czertainly.core.service.TspConfigurationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TspConfigurationControllerImpl implements TspConfigurationController {

    private final TspConfigurationService tspConfigurationService;

    @Autowired
    public TspConfigurationControllerImpl(TspConfigurationService tspConfigurationService) {
        this.tspConfigurationService = tspConfigurationService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.TSP_CONFIGURATION)
    @AuditLogged(module = Module.SIGNING, resource = Resource.TSP_CONFIGURATION, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return tspConfigurationService.getSearchableFieldInformation();
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.TSP_CONFIGURATION, operation = Operation.LIST)
    public PaginationResponseDto<TspConfigurationListDto> listTspConfigurations(SearchRequestDto request) {
        return tspConfigurationService.listTspConfigurations(request, SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.TSP_CONFIGURATION, operation = Operation.DETAIL)
    public TspConfigurationDto getTspConfiguration(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        return tspConfigurationService.getTspConfiguration(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.TSP_CONFIGURATION, operation = Operation.CREATE)
    public TspConfigurationDto createTspConfiguration(@Valid TspConfigurationRequestDto request) throws AttributeException, NotFoundException {
        return tspConfigurationService.createTspConfiguration(request);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.TSP_CONFIGURATION, operation = Operation.UPDATE)
    public TspConfigurationDto updateTspConfiguration(@LogResource(uuid = true) UUID uuid, @Valid TspConfigurationRequestDto request) throws NotFoundException, AttributeException {
        return tspConfigurationService.updateTspConfiguration(SecuredUUID.fromUUID(uuid), request);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.TSP_CONFIGURATION, operation = Operation.DELETE)
    public void deleteTspConfiguration(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        tspConfigurationService.deleteTspConfiguration(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.TSP_CONFIGURATION, operation = Operation.DELETE)
    public List<BulkActionMessageDto> bulkDeleteTspConfigurations(@LogResource(uuid = true) List<UUID> uuids) {
        return tspConfigurationService.bulkDeleteTspConfigurations(SecuredUUID.fromUuidList(uuids));
    }
}
