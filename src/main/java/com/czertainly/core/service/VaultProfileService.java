package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.secret.SecretType;
import com.czertainly.api.model.core.vaultprofile.VaultProfileDetailDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileListResponseDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileRequestDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileUpdateRequestDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface VaultProfileService {

    VaultProfileListResponseDto listVaultProfiles(SearchRequestDto searchRequest, SecurityFilter securityFilter);

    VaultProfileDetailDto getVaultProfileDetails(SecuredParentUUID vaultUuid, SecuredUUID vaultProfileUuid) throws NotFoundException;

    VaultProfileDetailDto updateVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID, VaultProfileUpdateRequestDto vaultProfileDetail) throws NotFoundException;

    void deleteVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException;

    VaultProfileDetailDto createVaultProfile(SecuredParentUUID securedParentUUID, VaultProfileRequestDto vaultProfileDetail) throws NotFoundException, AttributeException;

    void enableVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException;

    void disableVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException;

    List<BaseAttribute> getAttributesForCreatingSecret(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID, SecretType secretType);
}
