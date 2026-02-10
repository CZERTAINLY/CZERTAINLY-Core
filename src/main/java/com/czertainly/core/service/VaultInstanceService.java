package com.czertainly.core.service;


import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.vault.VaultInstanceDetailDto;
import com.czertainly.api.model.core.vault.VaultInstanceRequestDto;

import java.util.UUID;

public interface VaultInstanceService {


    VaultInstanceDetailDto createVaultInstance(VaultInstanceRequestDto vaultInstanceRequest) throws ConnectorException, NotFoundException, AttributeException;

    VaultInstanceDetailDto getVaultInstance(UUID uuid) throws ConnectorException, NotFoundException, AttributeException;


}
