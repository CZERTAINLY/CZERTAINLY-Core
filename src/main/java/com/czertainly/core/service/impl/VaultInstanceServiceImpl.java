package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.vault.*;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.VaultInstance;
import com.czertainly.core.dao.entity.VaultInstance_;
import com.czertainly.core.dao.repository.VaultInstanceRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.ResourceService;
import com.czertainly.core.service.VaultInstanceService;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.SearchHelper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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
    public VaultInstanceDetailDto createVaultInstance(VaultInstanceRequestDto request) throws ConnectorException, NotFoundException, AttributeException {

        if (vaultInstanceRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Vault Instance with the same name already exists");
        }

        ConnectorDto connector = connectorService.getConnector(SecuredUUID.fromUUID(request.getConnectorUuid()));

        attributeEngine.validateCustomAttributesContent(Resource.VAULT, request.getCustomAttributes());

        // Load complete credential data and resource data
        var dataAttributes = attributeEngine.getDataAttributesByContent(UUID.fromString(connector.getUuid()), request.getAttributes());
//        credentialService.loadFullCredentialData(dataAttributes); Credential data??
        resourceService.loadResourceObjectContentData(dataAttributes);
        // TODO: Merge and validate the attributes with the connector attributes for new connector version

        // TODO: Create the vault instance in connector using API

        VaultInstance vaultInstance = new VaultInstance();
        vaultInstance.setName(request.getName());
        vaultInstance.setConnectorUuid(request.getConnectorUuid());
        vaultInstanceRepository.save(vaultInstance);

        VaultInstanceDetailDto detailDto = vaultInstance.mapToDetailDto();
        detailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.VAULT, vaultInstance.getUuid(), request.getCustomAttributes()));
        detailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.VAULT, vaultInstance.getUuid(), request.getAttributes()));

        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT, action = ResourceAction.DETAIL)
    public VaultInstanceDetailDto getVaultInstance(UUID uuid) throws NotFoundException {
        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(SecuredUUID.fromUUID(uuid))
                .orElseThrow(() -> new NotFoundException(Resource.VAULT.getLabel(), uuid.toString()));
        VaultInstanceDetailDto detailDto = vaultInstance.mapToDetailDto();
        detailDto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.VAULT, uuid));
        detailDto.setAttributes(attributeEngine.getObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.VAULT, vaultInstance.getUuid()));
        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT, action = ResourceAction.LIST)
    public VaultInstanceListResponseDto listVaultInstances(SearchRequestDto request, SecurityFilter securityFilter) {
        Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        TriFunction<Root<VaultInstance>, CriteriaBuilder, CriteriaQuery<?>, Predicate> predicate = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, request.getFilters());
        List<VaultInstanceDto> vaultInstances = vaultInstanceRepository.findUsingSecurityFilter(securityFilter, List.of(), predicate, p,  (root, cb) -> cb.desc(root.get(VaultInstance_.CREATED))).stream().map(VaultInstance::mapToDto).toList();
        VaultInstanceListResponseDto response = new VaultInstanceListResponseDto();
        response.setVaultInstances(vaultInstances);
        response.setPageNumber(request.getPageNumber());
        response.setItemsPerPage(request.getItemsPerPage());
        response.setTotalItems(vaultInstanceRepository.countUsingSecurityFilter(securityFilter, predicate));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT, action = ResourceAction.DELETE)
    public void deleteVaultInstance(UUID uuid) throws NotFoundException {
        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(SecuredUUID.fromUUID(uuid))
                .orElseThrow(() -> new NotFoundException(Resource.VAULT.getLabel(), uuid.toString()));
        //TODO: check for existing profiles
        vaultInstanceRepository.delete(vaultInstance);
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT, action = ResourceAction.UPDATE)
    public VaultInstanceDetailDto updateVaultInstance(UUID uuid, VaultInstanceUpdateRequestDto request) throws NotFoundException, AttributeException {
        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(SecuredUUID.fromUUID(uuid))
                .orElseThrow(() -> new NotFoundException(Resource.VAULT.getLabel(), uuid.toString()));
        vaultInstance.setDescription(request.getDescription());
        vaultInstanceRepository.save(vaultInstance);

        VaultInstanceDetailDto detailDto = vaultInstance.mapToDetailDto();
        attributeEngine.validateCustomAttributesContent(Resource.VAULT, request.getCustomAttributes());
        detailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.VAULT, vaultInstance.getUuid(), request.getCustomAttributes()));

        // Load complete credential data and resource data
        var dataAttributes = attributeEngine.getDataAttributesByContent(vaultInstance.getConnectorUuid(), request.getAttributes());
//        credentialService.loadFullCredentialData(dataAttributes); Credential data??
        resourceService.loadResourceObjectContentData(dataAttributes);
        // TODO: Merge and validate the attributes with the connector attributes for new connector version

        // TODO: Create the vault instance in connector using API

        detailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.VAULT, vaultInstance.getUuid(), request.getAttributes()));

        return detailDto;
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine.getResourceSearchableFields(Resource.VAULT, false);

        List<SearchFieldDataDto> fields = List.of(
                SearchHelper.prepareSearch(FilterField.VAULT_INSTANCE_NAME),
                SearchHelper.prepareSearch(FilterField.VAULT_INSTANCE_CONNECTOR_NAME)
        );
        fields = new ArrayList<>(fields);
        fields.sort(new SearchFieldDataComparator());

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));

        return searchFieldDataByGroupDtos;
    }

    @Override
    public List<BaseAttribute> listVaultInstanceAttributes(UUID connectorUuid) {
        // TODO: Get the attributes from connector API based on the connector UUID
        return List.of();
    }

}
