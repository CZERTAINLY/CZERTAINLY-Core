package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.VaultProfileController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.connector.secrets.SecretType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileDetailDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileRequestDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileUpdateRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.VaultProfileService;
import com.czertainly.core.util.converter.SecretTypeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class VaultProfileControllerImpl implements VaultProfileController {

    private VaultProfileService vaultProfileService;

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(SecretType.class, new SecretTypeConverter());
    }

    @Autowired
    public void setVaultProfileService(VaultProfileService vaultProfileService) {
        this.vaultProfileService = vaultProfileService;
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT_PROFILE, operation = Operation.LIST)
    public PaginationResponseDto<VaultProfileDto> listVaultProfiles(SearchRequestDto searchRequest) {
        return vaultProfileService.listVaultProfiles(searchRequest, SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT_PROFILE, affiliatedResource = Resource.VAULT, operation = Operation.DETAIL)
    public VaultProfileDetailDto getVaultProfileDetails(@LogResource(uuid = true, affiliated = true) UUID vaultUuid, @LogResource(uuid = true) UUID vaultProfileUuid) throws NotFoundException {
        return vaultProfileService.getVaultProfileDetails(SecuredParentUUID.fromUUID(vaultUuid), SecuredUUID.fromUUID(vaultProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT_PROFILE, affiliatedResource = Resource.VAULT, operation = Operation.UPDATE)
    public VaultProfileDetailDto updateVaultProfile(@LogResource(uuid = true, affiliated = true) UUID vaultUuid, @LogResource(uuid = true) UUID vaultProfileUuid, VaultProfileUpdateRequestDto vaultProfileUpdateRequest) throws NotFoundException, AttributeException {
        return vaultProfileService.updateVaultProfile(SecuredParentUUID.fromUUID(vaultUuid), SecuredUUID.fromUUID(vaultProfileUuid), vaultProfileUpdateRequest);
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT_PROFILE, affiliatedResource = Resource.VAULT, operation = Operation.DELETE)
    public void deleteVaultProfile(@LogResource(uuid = true, affiliated = true) UUID vaultUuid, @LogResource(uuid = true) UUID vaultProfileUuid) throws NotFoundException {
        vaultProfileService.deleteVaultProfile(SecuredParentUUID.fromUUID(vaultUuid), SecuredUUID.fromUUID(vaultProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT_PROFILE, affiliatedResource = Resource.VAULT, operation = Operation.CREATE)
    public VaultProfileDetailDto createVaultProfile(@LogResource(uuid = true, affiliated = true) UUID vaultUuid, VaultProfileRequestDto vaultProfileRequest) throws NotFoundException, AttributeException, AlreadyExistException {
        return vaultProfileService.createVaultProfile(SecuredParentUUID.fromUUID(vaultUuid), vaultProfileRequest);
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT_PROFILE, affiliatedResource = Resource.VAULT, operation = Operation.ENABLE)
    public void enableVaultProfile(@LogResource(uuid = true, affiliated = true) UUID vaultUuid, @LogResource(uuid = true) UUID vaultProfileUuid) throws NotFoundException {
        vaultProfileService.enableVaultProfile(SecuredParentUUID.fromUUID(vaultUuid), SecuredUUID.fromUUID(vaultProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT_PROFILE, affiliatedResource = Resource.VAULT, operation = Operation.DISABLE)
    public void disableVaultProfile(@LogResource(uuid = true, affiliated = true) UUID vaultUuid, @LogResource(uuid = true) UUID vaultProfileUuid) throws NotFoundException {
        vaultProfileService.disableVaultProfile(SecuredParentUUID.fromUUID(vaultUuid), SecuredUUID.fromUUID(vaultProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.VAULT_PROFILE, affiliatedResource = Resource.VAULT, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> getAttributesForCreatingSecret(@LogResource(uuid = true, affiliated = true) UUID vaultUuid, @LogResource(uuid = true) UUID vaultProfileUuid, SecretType secretType) throws ConnectorException, NotFoundException, AttributeException {
        return vaultProfileService.getAttributesForCreatingSecret(SecuredParentUUID.fromUUID(vaultUuid), SecuredUUID.fromUUID(vaultProfileUuid), secretType);
    }

    @Override
    @AuditLogged(module = Module.SECRETS, resource = Resource.SEARCH_FILTER, affiliatedResource = Resource.VAULT_PROFILE, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return vaultProfileService.getSearchableFieldInformation();
    }
}
