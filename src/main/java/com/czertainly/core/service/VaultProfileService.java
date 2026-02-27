package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.connector.secrets.SecretType;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.vaultprofile.*;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface VaultProfileService {

    PaginationResponseDto<VaultProfileDto> listVaultProfiles(SearchRequestDto searchRequest, SecurityFilter securityFilter);

    VaultProfileDetailDto getVaultProfileDetails(SecuredParentUUID vaultUuid, SecuredUUID vaultProfileUuid) throws NotFoundException;

    VaultProfileDetailDto updateVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID, VaultProfileUpdateRequestDto vaultProfileDetail) throws NotFoundException;

    void deleteVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException;

    VaultProfileDetailDto createVaultProfile(SecuredParentUUID securedParentUUID, VaultProfileRequestDto vaultProfileDetail) throws NotFoundException, AttributeException, AlreadyExistException;

    void enableVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException;

    void disableVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException;

    List<BaseAttribute> getAttributesForCreatingSecret(SecuredParentUUID vaultUUID, SecuredUUID vaultProfileUUID, SecretType secretType) throws NotFoundException, ConnectorException;

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();
}
