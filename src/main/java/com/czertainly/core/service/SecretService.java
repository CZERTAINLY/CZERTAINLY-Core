package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.secret.*;
import com.czertainly.api.model.core.secret.content.SecretContent;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.UUID;

public interface SecretService {

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    SecretListResponseDto listSecrets(SearchRequestDto searchRequest, SecurityFilter securityFilter);

    SecretDetailDto createSecret(SecretRequestDto secretRequest, SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException, AttributeException;

    SecretDetailDto updateSecret(UUID uuid, SecretUpdateRequestDto secretRequest) throws NotFoundException, AttributeException;

    void deleteSecret(UUID uuid) throws NotFoundException;

    void enableSecret(UUID uuid) throws NotFoundException;

    void disableSecret(UUID uuid) throws NotFoundException;

    void addVaultProfileToSecret(UUID uuid, UUID vaultProfileUuid) throws NotFoundException;

    void removeVaultProfileFromSecret(UUID uuid, UUID vaultProfileUuid) throws NotFoundException;

    SecretDetailDto getSecretDetails(UUID uuid) throws NotFoundException;

    List<SecretVersionDto> getSecretVersions(UUID uuid) throws NotFoundException;

    SecretContent getSecretContent(UUID uuid) throws NotFoundException;
}
