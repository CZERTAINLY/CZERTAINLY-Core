package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AttributeApiClient;
import com.czertainly.api.clients.ConnectorApiClient;
import com.czertainly.api.clients.HealthApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorCommunicationException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.connector.ConnectDto;
import com.czertainly.api.model.client.connector.ConnectRequestDto;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.client.connector.ConnectorUpdateRequestDto;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.client.connector.InfoResponse;
import com.czertainly.api.model.common.HealthDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.connector.BaseFunctionGroupDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.EndpointDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.dao.entity.Endpoint;
import com.czertainly.core.dao.entity.EntityInstanceReference;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.Connector2FunctionGroupRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CredentialRepository;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.service.ComplianceProfileService;
import com.czertainly.core.service.ComplianceService;
import com.czertainly.core.service.ConnectorAuthService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.MetaDefinitions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class ConnectorServiceImpl implements ConnectorService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorServiceImpl.class);

    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;
    @Autowired
    private ConnectorApiClient connectorApiClient;
    @Autowired
    private AttributeApiClient attributeApiClient;
    @Autowired
    private HealthApiClient healthApiClient;
    @Autowired
    private CredentialRepository credentialRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    @Autowired
    private ConnectorAuthService connectorAuthService;

    @Autowired
    private ComplianceService complianceService;
    @Autowired
    private ComplianceProfileService complianceProfileService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.REQUEST)
    public List<ConnectorDto> listConnectors() {
        return connectorRepository.findAll().stream()
                .map(Connector::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.REQUEST)
    public List<ConnectorDto> listConnectorsByFunctionGroup(FunctionGroupCode functionGroup) {
        List<ConnectorDto> connectors = new ArrayList<>();

        // TODO - this can be done using one select similar to connectorRepository.findConnectedByFunctionGroupAndKind()

        for (Connector connector : connectorRepository.findByStatus(ConnectorStatus.CONNECTED)) {
            ConnectorDto connectorDto = connector.mapToDto();
            for (FunctionGroupDto fg : connectorDto.getFunctionGroups()) {
                if (functionGroup == FunctionGroupCode.AUTHORITY_PROVIDER) {
                    if (Arrays.asList(FunctionGroupCode.AUTHORITY_PROVIDER, FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER).contains(fg.getFunctionGroupCode())) {
                        connectorDto.setFunctionGroups(List.of(fg));
                        connectors.add(connectorDto);
                    }
                } else {
                    if (fg.getFunctionGroupCode() == functionGroup) {
                        connectorDto.setFunctionGroups(List.of(fg));
                        connectors.add(connectorDto);
                    }
                }
            }
        }
        return connectors;
    }

    @Override
    public List<Connector> listConnectorEntityByFunctionGroup(FunctionGroupCode functionGroup) {
        List<Connector> connectors = new ArrayList<>();

        for (Connector connector : connectorRepository.findByStatus(ConnectorStatus.CONNECTED)) {
            ConnectorDto connectorDto = connector.mapToDto();
            for (FunctionGroupDto fg : connectorDto.getFunctionGroups()) {
                if (functionGroup == FunctionGroupCode.AUTHORITY_PROVIDER) {
                    if (Arrays.asList(FunctionGroupCode.AUTHORITY_PROVIDER, FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER).contains(fg.getFunctionGroupCode())) {
                        connectors.add(connector);
                    }
                } else {
                    if (fg.getFunctionGroupCode() == functionGroup) {
                        connectorDto.setFunctionGroups(List.of(fg));
                        connectors.add(connector);
                    }
                }
            }
        }
        return connectors;
    }


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.REQUEST)
    public List<ConnectorDto> listConnectors(FunctionGroupCode functionGroupCode, String kind) throws NotFoundException {

        FunctionGroup functionGroup = functionGroupRepository.findByCode(functionGroupCode)
                .orElseThrow(() -> new NotFoundException(FunctionGroup.class, functionGroupCode));

        return connectorRepository.findConnectedByFunctionGroupAndKind(functionGroup, kind).stream()
                .map(Connector::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.REQUEST)
    public ConnectorDto getConnector(String uuid) throws ConnectorException {
        Connector connector = getConnectorEntity(uuid);
        ConnectorDto dto = connector.mapToDto();

        try {
            List<InfoResponse> functions = connectorApiClient.listSupportedFunctions(dto);
            for (FunctionGroupDto i : dto.getFunctionGroups()) {
                for (InfoResponse j : functions) {
                    if (i.getFunctionGroupCode() == j.getFunctionGroupCode()) {
                        i.setEndPoints(j.getEndPoints());
                    }
                }
            }
            if (!connector.getStatus().equals(ConnectorStatus.WAITING_FOR_APPROVAL)) {
                connector.setStatus(ConnectorStatus.CONNECTED);
            }
            connectorRepository.save(connector);
        } catch (ConnectorCommunicationException e) {
            connector.setStatus(ConnectorStatus.OFFLINE);
            connectorRepository.save(connector);
            dto.setStatus(ConnectorStatus.OFFLINE);
            dto.setFunctionGroups(new ArrayList<>());

            logger.error("Unable to fetch list of supported functions of connector " + dto.getName(), Exceptions.unwrap(e));
        }
        return dto;
    }

    @Override
    public Connector getConnectorEntity(String uuid) throws NotFoundException {
        return connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CREATE)
    public ConnectorDto createConnector(ConnectorRequestDto request) throws ConnectorException, AlreadyExistException {
        return createNewConnector(request, ConnectorStatus.CONNECTED);
    }

    @Override
    @Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR", "ROLE_ANONYMOUS"})
    public ConnectorDto createNewConnector(ConnectorRequestDto request, ConnectorStatus connectorStatus) throws ConnectorException, AlreadyExistException {
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("name must not be empty");
        }

        List<AttributeDefinition> authAttributes = connectorAuthService.mergeAndValidateAuthAttributes(request.getAuthType(), AttributeDefinitionUtils.getResponseAttributes(request.getAuthAttributes()));

        if (connectorRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(Connector.class, request.getName());
        }

        ConnectorDto connectorDto = new ConnectorDto();
        connectorDto.setName(request.getName());
        connectorDto.setUrl(request.getUrl());
        connectorDto.setAuthType(request.getAuthType());
        connectorDto.setAuthAttributes(AttributeDefinitionUtils.getResponseAttributes(authAttributes));

        List<ConnectDto> connectResponse = validateConnector(connectorDto);
        List<FunctionGroupDto> functionGroupDtos = new ArrayList<>();
        for (ConnectDto dto : connectResponse) {
            functionGroupDtos.add(dto.getFunctionGroup());
        }

        Connector connector = new Connector();
        connector.setName(request.getName());
        connector.setUrl(request.getUrl());
        connector.setAuthType(request.getAuthType());
        connector.setAuthAttributes(AttributeDefinitionUtils.serialize(authAttributes));
        connector.setStatus(connectorStatus);
        connectorRepository.save(connector);

        setFunctionGroups(functionGroupDtos, connector);

        complianceRuleGroupUpdate(connector, false);

        return connector.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CREATE)
    public ConnectorDto createConnector(ConnectorDto request, ConnectorStatus connectorStatus) throws NotFoundException, AlreadyExistException {
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("name must not be empty");
        }

        if (request.getFunctionGroups() == null) {
            throw new ValidationException("function groups must not be empty");
        }

        if (connectorRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(Connector.class, request.getName());
        }

        List<AttributeDefinition> authAttributes = connectorAuthService.mergeAndValidateAuthAttributes(request.getAuthType(), request.getAuthAttributes());

        Connector connector = new Connector();
        connector.setName(request.getName());
        connector.setUrl(request.getUrl());
        connector.setAuthType(request.getAuthType());
        connector.setAuthAttributes(AttributeDefinitionUtils.serialize(authAttributes));
        connector.setStatus(connectorStatus);
        connectorRepository.save(connector);

        setFunctionGroups(request.getFunctionGroups(), connector);

        complianceRuleGroupUpdate(connector, false);
        return connector.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CHANGE)
    public ConnectorDto updateConnector(String uuid, ConnectorUpdateRequestDto request) throws ConnectorException {
        List<AttributeDefinition> authAttributes = connectorAuthService.mergeAndValidateAuthAttributes(
                request.getAuthType(),
                AttributeDefinitionUtils.getResponseAttributes(request.getAuthAttributes()));

        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        if (request.getUrl() != null) {
            connector.setUrl(request.getUrl());
        }
        if (request.getAuthType() != null) {
            connector.setAuthType(request.getAuthType());
            connector.setAuthAttributes(AttributeDefinitionUtils.serialize(authAttributes));
        }

        connectorRepository.save(connector);

        List<ConnectDto> connectResponse = validateConnector(connector.mapToDto());
        List<FunctionGroupDto> functionGroupDtos = new ArrayList<>();
        for (ConnectDto dto : connectResponse) {
            functionGroupDtos.add(dto.getFunctionGroup());
        }
        if (!connector.getStatus().equals(ConnectorStatus.WAITING_FOR_APPROVAL)) {
            connector.setStatus(ConnectorStatus.CONNECTED);
        }
        setFunctionGroups(functionGroupDtos, connector);
        connectorRepository.save(connector);

        complianceRuleGroupUpdate(connector, true);

        return connector.mapToDto();
    }

    private Set<Connector2FunctionGroup> setFunctionGroups(List<FunctionGroupDto> functionGroups, Connector connector) throws NotFoundException {
        // adding phase
        for (FunctionGroupDto dto : functionGroups) {
            FunctionGroup functionGroup = functionGroupRepository.findByUuid(dto.getUuid())
                    .orElseThrow(() -> new NotFoundException(FunctionGroup.class, dto.getUuid()));

            Connector2FunctionGroup c2fg = connector2FunctionGroupRepository.findByConnectorAndFunctionGroup(connector, functionGroup).orElse(null);

            if (c2fg != null) {
                String dtoKinds = MetaDefinitions.serializeArrayString(dto.getKinds());
                if(!dtoKinds.equals(c2fg.getKinds())){
                    c2fg.setKinds(dtoKinds);
                    connector2FunctionGroupRepository.save(c2fg);
                }
                logger.debug("Connector {} already has function group {} - not added", connector.getName(), functionGroup.getName());

            } else {
                addFunctionGroupToConnector(functionGroup, dto.getKinds(), connector);
                logger.info("Added function group {} to connector {}", functionGroup.getName(), connector.getName());
            }
        }

        // removing phase
        for (Connector2FunctionGroup c2fg : new HashSet<>(connector.getFunctionGroups())) {
            Optional<FunctionGroupDto> dto = functionGroups.stream()
                    .filter(fg -> fg.getUuid().equals(c2fg.getFunctionGroup().getUuid()))
                    .findFirst();

            if (dto.isPresent()) {
                logger.debug("Connector {} still has function group {} - not removed", connector.getName(), dto.get().getName());
            } else {
                removeFunctionGroupFromConnector(c2fg.getFunctionGroup(), connector);
                logger.info("Removed function group {} from connector {}", c2fg.getFunctionGroup().getName(), connector.getName());
            }
        }

        return connector.getFunctionGroups();
    }

    private void addFunctionGroupToConnector(FunctionGroup functionGroup, List<String> kinds, Connector connector) throws NotFoundException {
        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setKinds(MetaDefinitions.serializeArrayString(kinds));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);
    }

    private void removeFunctionGroupFromConnector(FunctionGroup functionGroup, Connector connector) throws NotFoundException {
        Connector2FunctionGroup c2fg = connector2FunctionGroupRepository.findByConnectorAndFunctionGroup(connector, functionGroup)
                .orElseThrow(() -> new NotFoundException(Connector2FunctionGroup.class, String.format("connector=%s, functionGroup=%s", connector.getName(), functionGroup.getName())));

        connector2FunctionGroupRepository.delete(c2fg);

        connector.getFunctionGroups().remove(c2fg);
        connectorRepository.save(connector);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.DELETE)
    public void removeConnector(String uuid) throws NotFoundException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        List<ValidationError> errors = new ArrayList<>();
        if (!connector.getCredentials().isEmpty()) {
            errors.add(ValidationError.create(
                    "Connector {} has {} dependent credentials",
                    connector.getName(), connector.getCredentials().size()));
            connector.getCredentials().forEach(
                    c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!connector.getAuthorityInstanceReferences().isEmpty()) {
            errors.add(ValidationError.create(
                    "Connector {} has {} dependent Authority instances",
                    connector.getName(), connector.getAuthorityInstanceReferences().size()));
            connector.getAuthorityInstanceReferences().forEach(
                    c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!connector.getEntityInstanceReferences().isEmpty()) {
            errors.add(ValidationError.create(
                    "Connector {} has {} dependent Entity instances",
                    connector.getName(), connector.getEntityInstanceReferences().size()));
            connector.getEntityInstanceReferences().forEach(
                    c -> errors.add(ValidationError.create(c.getName())));
        }

        for (String complianceProfileName : complianceProfileService.isComplianceProviderAssociated(connector)) {
            errors.add(ValidationError.create(
                    "Connector {} has {} dependent Compliance Profile",
                    connector.getName(), complianceProfileName));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete connector", errors);
        }
        List<Connector2FunctionGroup> connector2FunctionGroups = connector2FunctionGroupRepository.findAllByConnector(connector);
        connector2FunctionGroupRepository.deleteAll(connector2FunctionGroups);
        connectorRepository.delete(connector);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CONNECT)
    public List<ConnectDto> connect(ConnectRequestDto request) throws ValidationException, ConnectorException {
        ConnectorDto dto = new ConnectorDto();
        dto.setAuthAttributes(AttributeDefinitionUtils.getResponseAttributes(request.getAuthAttributes()));
        dto.setAuthType(request.getAuthType());
        dto.setUrl(request.getUrl());
        dto.setUuid(request.getUuid());
        return validateConnector(dto);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.APPROVE)
    public void approve(List<String> uuids) throws NotFoundException, ValidationException {
        for (String uuid : uuids) {
            try {
                Connector connector = connectorRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

                if (ConnectorStatus.WAITING_FOR_APPROVAL.equals(connector.getStatus())) {
                    connector.setStatus(ConnectorStatus.CONNECTED);
                    connectorRepository.save(connector);
                    complianceRuleGroupUpdate(connector, false);
                } else {
                    logger.warn("Connector {} has unexpected status {}", connector.getName(), connector.getStatus());
                }
            } catch (NotFoundException e) {
                logger.warn("Unable to find the connector with uuid {}", uuid);
            }
        }
    }


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CONNECT)
    public void reconnect(List<String> uuids) throws ValidationException, ConnectorException {
        for (String uuid : uuids) {
            try {
                Connector connector = connectorRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

                List<ConnectDto> result = reValidateConnector(connector.mapToDto());

                List<FunctionGroupDto> functionGroups = result.stream()
                        .map(ConnectDto::getFunctionGroup)
                        .collect(Collectors.toList());

                setFunctionGroups(functionGroups, connector);

                complianceRuleGroupUpdate(connector, true);
            } catch (NotFoundException e) {
                logger.warn("Unable to find the connector with uuid {}", uuid);
            }
        }
    }


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CONNECT)
    public List<ConnectDto> reconnect(String uuid) throws ValidationException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        List<ConnectDto> result = reValidateConnector(connector.mapToDto());

        List<FunctionGroupDto> functionGroups = result.stream()
                .map(ConnectDto::getFunctionGroup)
                .collect(Collectors.toList());

        setFunctionGroups(functionGroups, connector);

        complianceRuleGroupUpdate(connector, true);

        return result;
    }

    private void complianceRuleGroupUpdate(Connector connector, Boolean update) {
        if (connector.mapToDto().getFunctionGroups().stream().map(FunctionGroupDto::getFunctionGroupCode)
                .collect(Collectors.toList()).contains(FunctionGroupCode.COMPLIANCE_PROVIDER) && !connector.getStatus().equals(ConnectorStatus.WAITING_FOR_APPROVAL)) {
            logger.info("Connector Implements Compliance Provider. Initiating request to update the rules and group for: {}", connector);
            try {
                if (update) {
                    complianceService.updateGroupsAndRules(connector);
                } else {
                    complianceService.addFetchGroupsAndRules(connector);
                }
            } catch (ConnectorException e) {
                logger.error(e.getMessage());
                logger.error("Unable to fetch groups and rules for Connector: {}", connector.getName());
            }
        }
    }

    public List<ConnectDto> reValidateConnector(ConnectorDto request) throws ConnectorException {
        List<InfoResponse> functions = connectorApiClient.listSupportedFunctions(request);
        return reValidateConnector(functions);
    }

    public List<ConnectDto> reValidateConnector(List<? extends BaseFunctionGroupDto> functions) {
        List<ValidationError> errors = new ArrayList<>();
        List<ConnectDto> responses = new ArrayList<>();

        for (BaseFunctionGroupDto f : functions) {
            Optional<FunctionGroup> functionGroup = functionGroupRepository
                    .findByCode(f.getFunctionGroupCode());

            if (functionGroup.isEmpty()) {
                errors.add(ValidationError.create("Function group {} doesn't exist.", f.getFunctionGroupCode()));
                continue;
            }

            for (Endpoint e : functionGroup.get().getEndpoints()) {
                EndpointDto endpoint = findEndpoint(f.getEndPoints(), e.mapToDto());
                if (Boolean.TRUE.equals(e.isRequired()) && endpoint == null) {
                    errors.add(ValidationError.create("Required endpoint {} not supported by connector.", e.getName()));
                }
            }

            ConnectDto response = new ConnectDto();
            FunctionGroupDto functionGroupDto = functionGroup.get().mapToDto();
            functionGroupDto.setKinds(f.getKinds());
            functionGroupDto.setEndPoints(f.getEndPoints());
            response.setFunctionGroup(functionGroupDto);
            responses.add(response);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Connector validation failed.", errors);
        }

        return responses;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.VALIDATE)
    public List<ConnectDto> validateConnector(ConnectorDto request) throws ConnectorException {
        List<InfoResponse> functions = connectorApiClient.listSupportedFunctions(request);
        return validateConnector(functions, request.getUuid());
    }

    @Override
    @Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR", "ROLE_ANONYMOUS"})
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.VALIDATE)
    public List<ConnectDto> validateConnector(List<? extends BaseFunctionGroupDto> functions, String uuid) {
        List<ValidationError> errors = new ArrayList<>();
        List<ConnectDto> responses = new ArrayList<>();

        List<FunctionGroupCode> connectFunctionGroupCodeList = functions.stream().map(BaseFunctionGroupDto::getFunctionGroupCode).collect(Collectors.toList());
        Map<FunctionGroupCode, List<String>> connectFunctionGroupKindMap = new HashMap<>();

        for (BaseFunctionGroupDto f : functions) {
            connectFunctionGroupKindMap.put(f.getFunctionGroupCode(), f.getKinds());
        }
        List<String> alreadyExistingConnector = new ArrayList<>();

        for (Connector connector : connectorRepository.findAll()) {
            if (connector.getUuid().equals(uuid)) {
                continue;
            }
            List<FunctionGroupCode> connectorFunctionGroups = connector.getFunctionGroups().stream().map(Connector2FunctionGroup::getFunctionGroup).collect(Collectors.toList()).stream().map(FunctionGroup::getCode).collect(Collectors.toList());
            if (connectFunctionGroupCodeList.equals(connectorFunctionGroups)) {
                Map<FunctionGroupCode, List<String>> connectorFunctionGroupKindMap = new HashMap<>();

                for (Connector2FunctionGroup f : connector.getFunctionGroups()) {
                    connectorFunctionGroupKindMap.put(f.getFunctionGroup().getCode(), MetaDefinitions.deserializeArrayString(f.getKinds()));
                }

                if (connectFunctionGroupKindMap.equals(connectorFunctionGroupKindMap)) {
                    alreadyExistingConnector.add(connector.getName());
                }
            }
        }

        if (!alreadyExistingConnector.isEmpty()) {
            errors.add(ValidationError.create("Connector(s) with same kinds already exists:" + String.join(",", alreadyExistingConnector)));
        }

        for (BaseFunctionGroupDto f : functions) {
            Optional<FunctionGroup> functionGroup = functionGroupRepository
                    .findByCode(f.getFunctionGroupCode());

            if (functionGroup.isEmpty()) {
                errors.add(ValidationError.create("Function group {} doesn't exist.", f.getFunctionGroupCode()));
                continue;
            }

            for (Endpoint e : functionGroup.get().getEndpoints()) {
                EndpointDto endpoint = findEndpoint(f.getEndPoints(), e.mapToDto());
                if (Boolean.TRUE.equals(e.isRequired()) && endpoint == null) {
                    errors.add(ValidationError.create("Required endpoint {} not supported by connector.", e.getName()));
                }
            }

            ConnectDto response = new ConnectDto();
            FunctionGroupDto functionGroupDto = functionGroup.get().mapToDto();
            functionGroupDto.setKinds(f.getKinds());
            functionGroupDto.setEndPoints(f.getEndPoints());
            response.setFunctionGroup(functionGroupDto);
            responses.add(response);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Connector validation failed.", errors);
        }

        return responses;
    }

    private EndpointDto findEndpoint(List<EndpointDto> endpoints, EndpointDto wanted) {
        return endpoints.stream()
                .filter(e ->
                        e.getName().equals(wanted.getName()) &&
                                e.getContext().equals(wanted.getContext()) &&
                                e.getMethod().equals(wanted.getMethod())
                )
                .findFirst()
                .orElse(null);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.APPROVE)
    public void approve(String uuid) throws NotFoundException, ValidationException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        if (ConnectorStatus.WAITING_FOR_APPROVAL.equals(connector.getStatus())) {
            connector.setStatus(ConnectorStatus.CONNECTED);
            connectorRepository.save(connector);
            complianceRuleGroupUpdate(connector, false);
        } else {
            throw new ValidationException(ValidationError.create("Connector {} has unexpected status {}", connector.getName(), connector.getStatus()));
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.HEALTH, operation = OperationType.REQUEST)
    public HealthDto checkHealth(String uuid) throws ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        return healthApiClient.checkHealth(connector.mapToDto());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> getAttributes(String uuid, FunctionGroupCode functionGroup, String functionGroupType) throws ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        validateFunctionGroup(connector, functionGroup);

        return attributeApiClient.listAttributeDefinitions(connector.mapToDto(), functionGroup, functionGroupType);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public void validateAttributes(String uuid, FunctionGroupCode functionGroup, List<RequestAttributeDto> attributes, String functionGroupType) throws ValidationException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        validateAttributes(connector, functionGroup, attributes, functionGroupType);
    }

    private void validateAttributes(Connector connector, FunctionGroupCode functionGroup, List<RequestAttributeDto> attributes, String functionGroupType) throws ValidationException, ConnectorException {
        validateFunctionGroup(connector, functionGroup);
        attributeApiClient.validateAttributes(connector.mapToDto(), functionGroup, attributes, functionGroupType);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public List<AttributeDefinition> mergeAndValidateAttributes(String uuid, FunctionGroupCode functionGroup, List<RequestAttributeDto> attributes, String functionGroupType) throws ValidationException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        List<AttributeDefinition> definitions = attributeApiClient.listAttributeDefinitions(connector.mapToDto(), functionGroup, functionGroupType);
        List<AttributeDefinition> merged = AttributeDefinitionUtils.mergeAttributes(definitions, attributes);

        validateAttributes(connector, functionGroup, attributes, functionGroupType);

        return merged;
    }

    private void validateFunctionGroup(Connector connector, FunctionGroupCode functionGroup) {
        Connector2FunctionGroup connector2FunctionGroup = null;
        for (Connector2FunctionGroup c2fg : connector.getFunctionGroups()) {
            if (c2fg.getFunctionGroup().getCode().equals(functionGroup)) {
                connector2FunctionGroup = c2fg;
            }
        }

        if (connector2FunctionGroup == null) {
            throw new ValidationException(ValidationError.create("Connector {} doesn't support function group code {}", connector.getName(), functionGroup));
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public Map<FunctionGroupCode, Map<String, List<AttributeDefinition>>> getAllAttributesOfConnector(String uuid) throws ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        Map<FunctionGroupCode, Map<String, List<AttributeDefinition>>> attributes = new HashMap<>();
        for (FunctionGroupDto fg : connector.mapToDto().getFunctionGroups()) {
            Map<String, List<AttributeDefinition>> kindsAttribute = new HashMap<>();
            for (String kind : fg.getKinds()) {
                kindsAttribute.put(kind, attributeApiClient.listAttributeDefinitions(connector.mapToDto(), fg.getFunctionGroupCode(), kind));
            }
            attributes.put(fg.getFunctionGroupCode(), kindsAttribute);
        }
        return attributes;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.DELETE)
    public List<ForceDeleteMessageDto> bulkRemoveConnector(List<String> uuids) throws ValidationException, NotFoundException {
        List<Connector> deletableConnectors = new ArrayList<>();
        List<ForceDeleteMessageDto> messages = new ArrayList<>();
        for (String uuid : uuids) {
            List<String> errors = new ArrayList<>();
            Connector connector = connectorRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException(Connector.class, uuid));


            if (!connector.getCredentials().isEmpty()) {
                errors.add("Dependent Credentials: " + connector.getCredentials().size() + ". Names: ");
                connector.getCredentials().forEach(
                        c -> errors.add(c.getName()));
            }

            if (!connector.getAuthorityInstanceReferences().isEmpty()) {
                errors.add("Authority instances: " + connector.getAuthorityInstanceReferences().size() + ". Names: ");
                connector.getAuthorityInstanceReferences().forEach(
                        c -> errors.add(c.getName()));
            }

            if (!connector.getEntityInstanceReferences().isEmpty()) {
                errors.add("Entity instances: " + connector.getEntityInstanceReferences().size() + ". Names: ");
                connector.getEntityInstanceReferences().forEach(
                        c -> errors.add(c.getName()));
            }

            Set<String> compProfiles = complianceProfileService.isComplianceProviderAssociated(connector);
            if (!compProfiles.isEmpty()) {
                errors.add("Compliance Profiles: " + String.join(", ", compProfiles));
            }

            if (!errors.isEmpty()) {
                ForceDeleteMessageDto forceModal = new ForceDeleteMessageDto();
                forceModal.setUuid(connector.getUuid());
                forceModal.setName(connector.getName());
                forceModal.setMessage(String.join(",", errors));
                messages.add(forceModal);
            } else {
                deletableConnectors.add(connector);
                if (connector.mapToDto().getFunctionGroups().stream().map(FunctionGroupDto::getFunctionGroupCode)
                        .collect(Collectors.toList()).contains(FunctionGroupCode.COMPLIANCE_PROVIDER)) {
                    complianceProfileService.removeRulesAndGroupForEmptyConnector(connector);
                }
            }
        }
        for (Connector connector : deletableConnectors) {
            List<Connector2FunctionGroup> connector2FunctionGroups = connector2FunctionGroupRepository.findAllByConnector(connector);
            connector2FunctionGroupRepository.deleteAll(connector2FunctionGroups);
            connectorRepository.delete(connector);
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.FORCE_DELETE)
    public void bulkForceRemoveConnector(List<String> uuids) throws ValidationException, NotFoundException {
        for (String uuid : uuids) {
            try {
                Connector connector = connectorRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

                if (!connector.getCredentials().isEmpty()) {
                    for (Credential credential : connector.getCredentials()) {
                        credential.setConnector(null);
                        credentialRepository.save(credential);
                    }
                    connector.getCredentials().removeAll(connector.getCredentials());
                    connectorRepository.save(connector);
                }

                if (!connector.getAuthorityInstanceReferences().isEmpty()) {
                    for (AuthorityInstanceReference ref : connector.getAuthorityInstanceReferences()) {
                        ref.setConnector(null);
                        authorityInstanceReferenceRepository.save(ref);
                    }
                    connector.getAuthorityInstanceReferences().removeAll(connector.getAuthorityInstanceReferences());
                    connectorRepository.save(connector);
                }

                if (!connector.getEntityInstanceReferences().isEmpty()) {
                    for (EntityInstanceReference ref : connector.getEntityInstanceReferences()) {
                        ref.setConnector(null);
                        entityInstanceReferenceRepository.save(ref);
                    }
                    connector.getEntityInstanceReferences().removeAll(connector.getEntityInstanceReferences());
                    connectorRepository.save(connector);
                }

                Set<String> compProfiles = complianceProfileService.isComplianceProviderAssociated(connector);
                if (!compProfiles.isEmpty()) {
                    complianceProfileService.nullifyComplianceProviderAssociation(connector);
                }

                List<Connector2FunctionGroup> connector2FunctionGroups = connector2FunctionGroupRepository.findAllByConnector(connector);
                connector2FunctionGroupRepository.deleteAll(connector2FunctionGroups);
                connectorRepository.delete(connector);
            } catch (NotFoundException e) {
                logger.warn("Unable to find connector with uuid {}. It may have deleted already", uuid);
            }
        }
    }
}
