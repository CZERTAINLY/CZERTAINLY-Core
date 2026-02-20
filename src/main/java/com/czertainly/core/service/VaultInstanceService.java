package com.czertainly.core.service;


import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.vault.VaultInstanceDetailDto;
import com.czertainly.api.model.core.vault.VaultInstanceListResponseDto;
import com.czertainly.api.model.core.vault.VaultInstanceRequestDto;
import com.czertainly.api.model.core.vault.VaultInstanceUpdateRequestDto;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.UUID;

public interface VaultInstanceService {


    VaultInstanceDetailDto createVaultInstance(VaultInstanceRequestDto vaultInstanceRequest) throws ConnectorException, NotFoundException, AttributeException, AlreadyExistException;

    VaultInstanceDetailDto getVaultInstance(UUID uuid) throws ConnectorException, NotFoundException, AttributeException;

    VaultInstanceListResponseDto listVaultInstances(SearchRequestDto searchRequest, SecurityFilter securityFilter);

    void deleteVaultInstance(UUID uuid) throws NotFoundException;

    VaultInstanceDetailDto updateVaultInstance(UUID uuid, VaultInstanceUpdateRequestDto vaultInstanceRequest) throws NotFoundException, AttributeException;

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    List<BaseAttribute> listVaultInstanceAttributes(UUID connectorUuid);
}
