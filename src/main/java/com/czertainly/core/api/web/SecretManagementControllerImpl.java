package com.czertainly.core.api.web;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.SecretManagementController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.secret.*;
import com.czertainly.api.model.core.secret.content.SecretContentDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.SecretService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;


@RestController
public class SecretManagementControllerImpl implements SecretManagementController {

    private SecretService secretService;

    @Autowired
    public void setSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SEARCH_FILTER, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return secretService.getSearchableFieldInformation();
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SECRET, operation = Operation.LIST)
    public SecretListResponseDto listSecrets(SearchRequestDto searchRequest) {
        return secretService.listSecrets(searchRequest, SecurityFilter.create());
    }

    @Override
    public SecretDetailDto getSecretDetails(UUID uuid) {
        return null;
    }

    @Override
    public List<SecretVersionDto> getSecretVersions(UUID uuid) {
        return List.of();
    }

    @Override
    public SecretContentDto getSecretContent(UUID uuid) {
        return null;
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SECRET, operation = Operation.CREATE, affiliatedResource = Resource.VAULT)
    public SecretDetailDto createSecret(SecretRequestDto secretRequest, @LogResource(uuid = true, affiliated = true) UUID vaultProfileUuid, @LogResource(uuid = true) UUID vaultUuid) throws NotFoundException, AttributeException {
        return secretService.createSecret(secretRequest, SecuredParentUUID.fromUUID(vaultProfileUuid), SecuredUUID.fromUUID(vaultUuid));
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SECRET, operation = Operation.UPDATE)
    public SecretDetailDto updateSecret(@LogResource(uuid = true) UUID uuid, SecretUpdateRequestDto secretRequest) throws NotFoundException, AttributeException {
        return secretService.updateSecret(uuid, secretRequest);
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SECRET, operation = Operation.DELETE)
    public void deleteSecret(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        secretService.deleteSecret(uuid);
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SECRET, operation = Operation.ENABLE)
    public void enableSecret(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        secretService.enableSecret(uuid);
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SECRET, operation = Operation.DISABLE)
    public void disableSecret(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        secretService.disableSecret(uuid);
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SECRET, affiliatedResource = Resource.VAULT_PROFILE, operation = Operation.ASSOCIATE)
    public void addVaultProfileToSecret(@LogResource(uuid = true) UUID uuid, @LogResource(uuid = true, affiliated = true) UUID vaultProfileUuid) throws NotFoundException {
        secretService.addVaultProfileToSecret(uuid, vaultProfileUuid);
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SECRET, affiliatedResource = Resource.VAULT_PROFILE, operation = Operation.DISASSOCIATE)
    public void removeVaultProfileFromSecret(@LogResource(uuid = true) UUID uuid, @LogResource(uuid = true, affiliated = true) UUID vaultProfileUuid) {
        secretService.removeVaultProfileFromSecret(uuid, vaultProfileUuid);
    }
}
