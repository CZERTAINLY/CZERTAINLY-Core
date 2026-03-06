package com.czertainly.core.service.impl;

import com.czertainly.api.clients.secret.VaultApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.v2.ConnectorDetailDto;
import com.czertainly.api.model.core.connector.v2.ConnectorInterfaceDto;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.vault.*;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.ConnectorRequestAttributesBuilder;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.Audited_;
import com.czertainly.core.dao.entity.VaultInstance;
import com.czertainly.core.dao.entity.VaultInstance_;
import com.czertainly.core.dao.repository.VaultInstanceRepository;
import com.czertainly.core.dao.repository.VaultProfileRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.VaultInstanceService;
import com.czertainly.core.service.v2.ConnectorService;
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


    private VaultProfileRepository vaultProfileRepository;
    private final VaultInstanceRepository vaultInstanceRepository;

    private VaultApiClient vaultApiClient;

    private ConnectorService connectorService;

    private AttributeEngine attributeEngine;
    private ConnectorRequestAttributesBuilder connectorRequestAttributesBuilder;


    @Autowired
    public void setConnectorRequestAttributesBuilder(ConnectorRequestAttributesBuilder connectorRequestAttributesBuilder) {
        this.connectorRequestAttributesBuilder = connectorRequestAttributesBuilder;
    }


    @Autowired
    public void setVaultApiClient(VaultApiClient vaultApiClient) {
        this.vaultApiClient = vaultApiClient;
    }

    @Autowired
    public void setVaultProfileRepository(VaultProfileRepository vaultProfileRepository) {
        this.vaultProfileRepository = vaultProfileRepository;
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
    public VaultInstanceDetailDto createVaultInstance(VaultInstanceRequestDto request) throws ConnectorException, NotFoundException, AttributeException, AlreadyExistException {

        if (vaultInstanceRepository.existsByName(request.getName())) {
            throw new AlreadyExistException("Vault Instance with the same name already exists");
        }

        ConnectorDetailDto connector = connectorService.getConnector(SecuredUUID.fromUUID(request.getConnectorUuid()));
        if (connector.getInterfaces().stream().map(ConnectorInterfaceDto::getUuid).noneMatch(request.getInterfaceUuid()::equals)) {
            throw new ValidationException("Connector does not have interface with UUID " + request.getInterfaceUuid());
        }
        checkConnectionToVaultInConnector(request.getConnectorUuid(), request.getAttributes(), connector);

        attributeEngine.validateCustomAttributesContent(Resource.VAULT, request.getCustomAttributes());

        VaultInstance vaultInstance = new VaultInstance();
        vaultInstance.setName(request.getName());
        vaultInstance.setConnectorUuid(request.getConnectorUuid());
        vaultInstance.setDescription(request.getDescription());
        vaultInstance.setConnectorInterfaceUuid(request.getInterfaceUuid());
        vaultInstanceRepository.save(vaultInstance);

        VaultInstanceDetailDto detailDto = vaultInstance.mapToDetailDto();
        detailDto.getConnector().setName(connector.getName());
        detailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.VAULT, vaultInstance.getUuid(), request.getCustomAttributes()));
        detailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.VAULT, vaultInstance.getUuid(), request.getAttributes()));

        return detailDto;
    }

    private void checkConnectionToVaultInConnector(UUID connectorUuid, List<RequestAttribute> requestAttributes, ConnectorDetailDto connector) throws ConnectorException, NotFoundException, AttributeException {
        List<BaseAttribute> attributes = listVaultInstanceAttributes(connectorUuid);
        List<RequestAttribute> connectorRequestAttributes = connectorRequestAttributesBuilder.prepareRequestAttributesForConnectorRequest(connectorUuid, attributes, requestAttributes);
        vaultApiClient.checkVaultConnection(connector, connectorRequestAttributes);
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
    public PaginationResponseDto<VaultInstanceDto> listVaultInstances(SearchRequestDto request, SecurityFilter securityFilter) {
        Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        TriFunction<Root<VaultInstance>, CriteriaBuilder, CriteriaQuery<?>, Predicate> predicate = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, request.getFilters());
        List<VaultInstanceDto> vaultInstances = vaultInstanceRepository.findUsingSecurityFilter(securityFilter, List.of(VaultInstance_.CONNECTOR), predicate, p,  (root, cb) -> cb.desc(root.get(Audited_.CREATED)))
                .stream().map(VaultInstance::mapToDto).toList();
        PaginationResponseDto<VaultInstanceDto> response = new PaginationResponseDto<>();
        response.setItems(vaultInstances);
        response.setPageNumber(request.getPageNumber());
        response.setItemsPerPage(request.getItemsPerPage());
        Long totalItems = vaultInstanceRepository.countUsingSecurityFilter(securityFilter, predicate);
        response.setTotalPages((int) Math.ceil((double) totalItems / request.getItemsPerPage()));
        response.setTotalItems(totalItems);
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT, action = ResourceAction.DELETE)
    public void deleteVaultInstance(UUID uuid) throws NotFoundException {
        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(SecuredUUID.fromUUID(uuid))
                .orElseThrow(() -> new NotFoundException(Resource.VAULT.getLabel(), uuid.toString()));
        List<String> allByVaultInstanceUuid = vaultProfileRepository.findAllNamesByVaultInstanceUuid(vaultInstance.getUuid());
        if (!allByVaultInstanceUuid.isEmpty()) {
            throw new ValidationException("Vault Instance %s is used in Vault Profiles %s".formatted(uuid, allByVaultInstanceUuid));
        }
        vaultInstanceRepository.delete(vaultInstance);
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT, action = ResourceAction.UPDATE)
    public VaultInstanceDetailDto updateVaultInstance(UUID uuid, VaultInstanceUpdateRequestDto request) throws NotFoundException, AttributeException, ConnectorException {
        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(SecuredUUID.fromUUID(uuid))
                .orElseThrow(() -> new NotFoundException(Resource.VAULT.getLabel(), uuid.toString()));
        vaultInstance.setDescription(request.getDescription());
        vaultInstanceRepository.save(vaultInstance);

        VaultInstanceDetailDto detailDto = vaultInstance.mapToDetailDto();
        attributeEngine.validateCustomAttributesContent(Resource.VAULT, request.getCustomAttributes());

        ConnectorDetailDto connector = connectorService.getConnector(SecuredUUID.fromUUID(vaultInstance.getConnectorUuid()));
        checkConnectionToVaultInConnector(vaultInstance.getConnectorUuid(), request.getAttributes(), connector);

        detailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.VAULT, vaultInstance.getUuid(), request.getAttributes()));
        detailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.VAULT, vaultInstance.getUuid(), request.getCustomAttributes()));

        return detailDto;
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine.getResourceSearchableFields(Resource.VAULT, false);

        List<SearchFieldDataDto> fields = List.of(
                SearchHelper.prepareSearch(FilterField.VAULT_INSTANCE_NAME),
                SearchHelper.prepareSearch(FilterField.VAULT_INSTANCE_CONNECTOR_NAME, vaultInstanceRepository.findAllConnectorNames())
        );
        fields = new ArrayList<>(fields);
        fields.sort(new SearchFieldDataComparator());

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));

        return searchFieldDataByGroupDtos;
    }

    @Override
    public List<BaseAttribute> listVaultInstanceAttributes(UUID connectorUuid) throws ConnectorException, NotFoundException {
        return vaultApiClient.listVaultAttributes(connectorService.getConnector(SecuredUUID.fromUUID(connectorUuid)));
    }
}
