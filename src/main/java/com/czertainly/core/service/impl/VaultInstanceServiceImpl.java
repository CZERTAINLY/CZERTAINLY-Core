package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.vault.VaultInstanceDetailDto;
import com.czertainly.api.model.core.vault.VaultInstanceRequestDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.VaultInstance;
import com.czertainly.core.dao.repository.VaultInstanceRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.ResourceService;
import com.czertainly.core.service.VaultInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class VaultInstanceServiceImpl implements VaultInstanceService {


    private final VaultInstanceRepository vaultInstanceRepository;

    private ConnectorService connectorService;
    private ResourceService resourceService;

    private AttributeEngine attributeEngine;

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public VaultInstanceServiceImpl(VaultInstanceRepository vaultInstanceRepository) {
        this.vaultInstanceRepository = vaultInstanceRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT, action = ResourceAction.CREATE)
    public VaultInstanceDetailDto createVaultInstance(VaultInstanceRequestDto vaultInstanceRequest) throws ConnectorException, NotFoundException, AttributeException {

        if (vaultInstanceRepository.existsByName(vaultInstanceRequest.getName())) {
            throw new IllegalArgumentException("Vault Instance with the same name already exists");
        }

        ConnectorDto connector = connectorService.getConnector(SecuredUUID.fromUUID(vaultInstanceRequest.getConnectorUuid()));

        attributeEngine.validateCustomAttributesContent(Resource.VAULT, vaultInstanceRequest.getCustomAttributes());

        // Load complete credential data and resource data
        var dataAttributes = attributeEngine.getDataAttributesByContent(UUID.fromString(connector.getUuid()), vaultInstanceRequest.getAttributes());
//        credentialService.loadFullCredentialData(dataAttributes); Credential data??
        resourceService.loadResourceObjectContentData(dataAttributes);
        // TODO: Merge and validate the attributes with the connector attributes for new connector version

        // TODO: Create the vault instance in connector using API

        VaultInstance vaultInstance = new VaultInstance();
        vaultInstance.setName(vaultInstanceRequest.getName());
        vaultInstance.setConnectorUuid(vaultInstanceRequest.getConnectorUuid());
        vaultInstanceRepository.save(vaultInstance);

        VaultInstanceDetailDto detailDto = vaultInstance.mapToDetailDto();
        detailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.VAULT, vaultInstance.getUuid(), vaultInstanceRequest.getCustomAttributes()));
        detailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.VAULT, vaultInstance.getUuid(), vaultInstanceRequest.getAttributes()));

        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT, action = ResourceAction.DETAIL)
    public VaultInstanceDetailDto getVaultInstance(UUID uuid) throws NotFoundException, AttributeException {
        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(SecuredUUID.fromUUID(uuid))
                .orElseThrow(() -> new NotFoundException(Resource.VAULT.getLabel(), uuid.toString()));
        VaultInstanceDetailDto detailDto = vaultInstance.mapToDetailDto();
        detailDto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.AUTHORITY, uuid));
        detailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.VAULT, vaultInstance.getUuid(), vaultInstanceRequest.getAttributes()));

        return detailDto;
    }


}
