package com.czertainly.core.service.v2.impl;

import com.czertainly.api.clients.v2.HealthApiClient;
import com.czertainly.api.clients.v2.InfoApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.connector.ConnectRequestDto;
import com.czertainly.api.model.client.connector.v2.ConnectorInfo;
import com.czertainly.api.model.client.connector.v2.HealthInfo;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.v2.*;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.ComplianceProfileRepository;
import com.czertainly.core.dao.repository.Connector2FunctionGroupRepository;
import com.czertainly.core.dao.repository.ConnectorInterfaceRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.v2.ConnectorService;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.RequestValidatorHelper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service(Resource.Codes.CONNECTOR)
@Transactional
public class ConnectorServiceImpl implements ConnectorService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorServiceImpl.class);

    private InfoApiClient infoApiClient;
    private HealthApiClient healthApiClient;
    private ConnectorRepository connectorRepository;

    private AttributeEngine attributeEngine;
    private ComplianceProfileRepository complianceProfileRepository;
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;
    private ConnectorInterfaceRepository connectorInterfaceRepository;

    @Autowired
    public void setInfoApiClient(InfoApiClient infoApiClient) {
        this.infoApiClient = infoApiClient;
    }

    @Autowired
    public void setHealthApiClient(HealthApiClient healthApiClient) {
        this.healthApiClient = healthApiClient;
    }

    @Autowired
    public void setConnectorRepository(ConnectorRepository connectorRepository) {
        this.connectorRepository = connectorRepository;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setComplianceProfileRepository(ComplianceProfileRepository complianceProfileRepository) {
        this.complianceProfileRepository = complianceProfileRepository;
    }

    @Autowired
    public void setConnector2FunctionGroupRepository(Connector2FunctionGroupRepository connector2FunctionGroupRepository) {
        this.connector2FunctionGroupRepository = connector2FunctionGroupRepository;
    }

    @Autowired
    public void setConnectorInterfaceRepository(ConnectorInterfaceRepository connectorInterfaceRepository) {
        this.connectorInterfaceRepository = connectorInterfaceRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.LIST)
    public PaginationResponseDto<ConnectorDto> listConnectors(SecurityFilter filter, SearchRequestDto request) {
        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<Connector>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<ConnectorDto> connectorDtos = connectorRepository.findUsingSecurityFilter(filter, List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get("created")))
                .stream()
                .map(Connector::mapToDto).toList();
        final Long maxItems = connectorRepository.countUsingSecurityFilter(filter, additionalWhereClause);

        PaginationResponseDto<ConnectorDto> response = new PaginationResponseDto<>();
        response.setItems(connectorDtos);
        response.setPageNumber(request.getPageNumber());
        response.setItemsPerPage(request.getItemsPerPage());
        response.setTotalItems(maxItems);
        response.setTotalPages((int) Math.ceil((double) maxItems / request.getItemsPerPage()));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.DETAIL)
    public ConnectorDetailDto getConnector(SecuredUUID uuid) throws NotFoundException {
        Connector connector = getConnectorEntity(uuid);
        ConnectorDetailDto dto = connector.mapToDetailDto();
    }

    @Override
    public ConnectorDetailDto createConnector(ConnectorRequestDto request) {
        return null;
    }

    @Override
    public ConnectorDetailDto editConnector(SecuredUUID uuid, ConnectorUpdateRequestDto request) {
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.DELETE)
    public void deleteConnector(SecuredUUID uuid) throws NotFoundException {
        Connector connector = getConnectorEntity(uuid);
        deleteConnector(connector);
    }

    @Override
    public List<ConnectInfo> connect(ConnectRequestDto request) {
        return List.of();
    }

    @Override
    public ConnectInfo reconnect(SecuredUUID uuid) {
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.APPROVE)
    public void approve(SecuredUUID uuid) throws NotFoundException {
        Connector connector = getConnectorEntity(uuid);

        if (connector.getStatus() == ConnectorStatus.WAITING_FOR_APPROVAL) {
            connector.setStatus(ConnectorStatus.CONNECTED);
            connectorRepository.save(connector);
        } else {
            throw new ValidationException(ValidationError.create("Connector '{}' has unexpected status {}", connector.getName(), connector.getStatus().getLabel()));
        }
    }

    @Override
    public void bulkApprove(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            try {
                approve(uuid);
            } catch (ValidationException e) {
                logger.warn(e.getMessage());
            } catch (NotFoundException e) {
                logger.warn("Unable to find the connector with uuid {}", uuid);
            }
        }
    }

    @Override
    public void bulkReconnect(List<SecuredUUID> uuids) {

    }

    @Override
    public List<BulkActionMessageDto> bulkDeleteConnector(List<SecuredUUID> uuids) {
        return List.of();
    }

    @Override
    public List<BulkActionMessageDto> forceDeleteConnector(List<SecuredUUID> uuids) {
        return List.of();
    }

    @Override
    public HealthInfo checkHealth(SecuredUUID uuid) throws NotFoundException, ConnectorException {
        return healthApiClient.checkHealth(getConnectorEntity(uuid).mapToDetailDto());
    }

    @Override
    public ConnectorInfo getInfo(SecuredUUID uuid) throws NotFoundException, ConnectorException {
        return infoApiClient.getConnectorInfo(getConnectorEntity(uuid).mapToDetailDto()).getConnector();
    }

    @Override
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        return connectorRepository.findResourceObject(objectUuid, Connector_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination) {
        return connectorRepository.listResourceObjects(filter, Connector_.name, null, pagination);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getConnectorEntity(uuid);
    }

    private Connector getConnectorEntity(SecuredUUID uuid) throws NotFoundException {
        return connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));
    }

    private void deleteConnector(Connector connector) {
        List<String> errors = new ArrayList<>();
        if (!connector.getCredentials().isEmpty()) {
            errors.add("Dependent credentials: " + String.join(", ", connector.getCredentials().stream().map(Credential::getName).collect(Collectors.toSet())));
        }

        if (!connector.getAuthorityInstanceReferences().isEmpty()) {
            errors.add("Dependent Authority Instances: " + String.join(", ", connector.getAuthorityInstanceReferences().stream().map(AuthorityInstanceReference::getName).collect(Collectors.toSet())));
        }

        if (!connector.getEntityInstanceReferences().isEmpty()) {
            errors.add("Dependent Entity Instances: " + String.join(", ", connector.getEntityInstanceReferences().stream().map(EntityInstanceReference::getName).collect(Collectors.toSet())));
        }

        if (!connector.getTokenInstanceReferences().isEmpty()) {
            errors.add("Dependent Token Instances: " + String.join(", ", connector.getTokenInstanceReferences().stream().map(TokenInstanceReference::getName).collect(Collectors.toSet())));
        }

        Set<String> complianceProfileNames = complianceProfileRepository.findDistinctByComplianceRulesConnectorUuid(connector.getUuid()).stream().map(ComplianceProfile::getName).collect(Collectors.toSet());
        if (!complianceProfileNames.isEmpty()) {
            errors.add("Dependent Compliance Profiles: " + String.join(", ", complianceProfileNames));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(ValidationError.create(String.join("\n", errors)));
        }

        // remove connector attribute definitions and content if they are only dependent resources
        attributeEngine.deleteConnectorAttributeDefinitionsContent(connector.getUuid());

        List<Connector2FunctionGroup> connector2FunctionGroups = connector2FunctionGroupRepository.findAllByConnector(connector);
        connector2FunctionGroupRepository.deleteAll(connector2FunctionGroups);

        attributeEngine.deleteAllObjectAttributeContent(Resource.CONNECTOR, connector.getUuid());
        connectorRepository.delete(connector);
    }

}
