package com.czertainly.core.api.web;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.VaultInstanceController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.vault.VaultInstanceDetailDto;
import com.czertainly.api.model.core.vault.VaultInstanceListResponseDto;
import com.czertainly.api.model.core.vault.VaultInstanceRequestDto;
import com.czertainly.api.model.core.vault.VaultInstanceUpdateRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.service.VaultInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class VaultInstanceControllerImpl implements VaultInstanceController {


    private final VaultInstanceService vaultInstanceService;

    @Autowired
    public VaultInstanceControllerImpl(VaultInstanceService vaultInstanceService) {
        this.vaultInstanceService = vaultInstanceService;
    }

    @Override
    public List<BaseAttribute> listVaultInstanceAttributes() {
        return List.of();
    }

    @Override
    public VaultInstanceDetailDto getVaultInstanceDetails(UUID uuid) throws ConnectorException, NotFoundException, AttributeException {
        return vaultInstanceService.getVaultInstance(uuid);
    }

    @Override
    public VaultInstanceListResponseDto listVaultInstances(SearchRequestDto searchRequest) {
        return null;
    }

    @Override
    public void deleteVaultInstance(UUID uuid) {

    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT, operation = Operation.CREATE)
    public VaultInstanceDetailDto createVaultInstance(VaultInstanceRequestDto vaultInstanceRequest) throws ConnectorException, NotFoundException, AttributeException {
        return vaultInstanceService.createVaultInstance(vaultInstanceRequest);
    }

    @Override
    public VaultInstanceDetailDto updateVaultInstance(UUID uuid, VaultInstanceUpdateRequestDto vaultInstanceRequest) throws ConnectorException, NotFoundException {
        return null;
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return List.of();
    }
}
