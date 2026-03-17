package com.czertainly.core.service;


import com.czertainly.api.clients.ApiClientConnectorInfo;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.connector.secrets.SecretOperationRequest;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.vault.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.UUID;

public interface VaultInstanceService {


    VaultInstanceDetailDto createVaultInstance(VaultInstanceRequestDto vaultInstanceRequest) throws ConnectorException, NotFoundException, AttributeException, AlreadyExistException;

    VaultInstanceDetailDto getVaultInstance(UUID uuid) throws ConnectorException, NotFoundException, AttributeException;

    PaginationResponseDto<VaultInstanceDto> listVaultInstances(SearchRequestDto searchRequest, SecurityFilter securityFilter);

    void deleteVaultInstance(UUID uuid) throws NotFoundException;

    VaultInstanceDetailDto updateVaultInstance(UUID uuid, VaultInstanceUpdateRequestDto vaultInstanceRequest) throws NotFoundException, AttributeException, ConnectorException;

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    List<BaseAttribute> listVaultInstanceAttributes(UUID connectorUuid) throws ConnectorException, NotFoundException, AttributeException;

    List<BaseAttribute> listVaultProfileAttributes(SecuredUUID vaultInstanceUuid) throws ConnectorException, NotFoundException, AttributeException;

    void loadAttributesForSecretOperation(ApiClientConnectorInfo connector, UUID vaultInstanceUuid, UUID vaultProfileUuid, SecretOperationRequest secretOperationRequest) throws NotFoundException, ConnectorException, AttributeException;
}
