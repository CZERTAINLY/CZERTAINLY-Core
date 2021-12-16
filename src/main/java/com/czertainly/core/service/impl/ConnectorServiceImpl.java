package com.czertainly.core.service.impl;

import com.czertainly.api.AttributeApiClient;
import com.czertainly.api.ConnectorApiClient;
import com.czertainly.api.HealthApiClient;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.AttributeCallback;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.HealthDto;
import com.czertainly.api.model.connector.*;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CoreCallbackService;
import com.czertainly.core.service.CredentialService;
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
import java.util.*;
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
    private CoreCallbackService coreCallbackService;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private CredentialRepository credentialRepository;
    @Autowired
    private CAInstanceReferenceRepository caInstanceReferenceRepository;

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
                if(functionGroup == FunctionGroupCode.CA_CONNECTOR){
                    if (Arrays.asList(FunctionGroupCode.CA_CONNECTOR, FunctionGroupCode.LEGACY_CA_CONNECTOR).contains(fg.getFunctionGroupCode())) {
                        connectorDto.setFunctionGroups(Arrays.asList(fg));
                        connectors.add(connectorDto);
                    }
                }else {
                    if (fg.getFunctionGroupCode() == functionGroup) {
                        connectorDto.setFunctionGroups(Arrays.asList(fg));
                        connectors.add(connectorDto);
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
    public ConnectorDto getConnector(String uuid) throws NotFoundException, ConnectorException {
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
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.REQUEST)
    public Connector getConnectorEntity(String uuid) throws NotFoundException {
        return connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CREATE)
    public ConnectorDto createConnector(ConnectorRequestDto request) throws ConnectorException, AlreadyExistException {
        return createNewConnector(request, ConnectorStatus.CONNECTED);
    }

    public ConnectorDto createNewConnector(ConnectorRequestDto request, ConnectorStatus connectorStatus) throws ConnectorException, AlreadyExistException {
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("name must not be empty");
        }

        if (connectorRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(Connector.class, request.getName());
        }

        ConnectorDto connectorDto = new ConnectorDto();
        connectorDto.setName(request.getName());
        connectorDto.setUrl(request.getUrl());
        connectorDto.setAuthType(request.getAuthType());
        connectorDto.setAuthAttributes(request.getAuthAttributes());

        List<ConnectDto> connectResponse = validateConnector(connectorDto);
        List<FunctionGroupDto> functionGroupDtos = new ArrayList<>();
        for(ConnectDto dto: connectResponse){
            functionGroupDtos.add(dto.getFunctionGroup());
        }

        Connector connector = new Connector();
        connector.setName(request.getName());
        connector.setUrl(request.getUrl());
        connector.setAuthType(request.getAuthType());
        connector.setAuthAttributes(AttributeDefinitionUtils.serialize(request.getAuthAttributes()));
        connector.setStatus(connectorStatus);
        connectorRepository.save(connector);

        setFunctionGroups(functionGroupDtos, connector);

        return connector.mapToDto();
    }

    @Override
    @Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR", "ROLE_ANONYMOUS"})
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

        Connector connector = new Connector();
        connector.setName(request.getName());
        connector.setUrl(request.getUrl());
        connector.setAuthType(request.getAuthType());
        connector.setAuthAttributes(AttributeDefinitionUtils.serialize(request.getAuthAttributes()));
        connector.setStatus(connectorStatus);
        connectorRepository.save(connector);

        setFunctionGroups(request.getFunctionGroups(), connector);

        return connector.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CHANGE)
    public ConnectorDto updateConnector(String uuid, ConnectorRequestDto request) throws ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        connector.setUrl(request.getUrl());
        connector.setAuthType(request.getAuthType());
        connector.setAuthAttributes(AttributeDefinitionUtils.serialize(request.getAuthAttributes()));

        connectorRepository.save(connector);

        List<ConnectDto> connectResponse = validateConnector(connector.mapToDto());
        List<FunctionGroupDto> functionGroupDtos = new ArrayList<>();
        for(ConnectDto dto: connectResponse){
            functionGroupDtos.add(dto.getFunctionGroup());
        }
        connector.setStatus(ConnectorStatus.CONNECTED);
        setFunctionGroups(functionGroupDtos, connector);
        connectorRepository.save(connector);

        return connector.mapToDto();
    }

    private Set<Connector2FunctionGroup> setFunctionGroups(List<FunctionGroupDto> functionGroups, Connector connector) throws NotFoundException {
        // adding phase
        for (FunctionGroupDto dto : functionGroups) {
            FunctionGroup functionGroup = functionGroupRepository.findById(dto.getId())
                    .orElseThrow(() -> new NotFoundException(FunctionGroup.class, dto.getId()));

            Optional<Connector2FunctionGroup> c2fg = connector2FunctionGroupRepository.findByConnectorAndFunctionGroup(connector, functionGroup);

            if (c2fg.isPresent()) {
                logger.debug("Connector {} already has function group {} - not added", connector.getName(), functionGroup.getName());
            } else {
                addFunctionGroupToConnector(functionGroup, dto.getKinds(), connector);
                logger.info("Added function group {} to connector {}", functionGroup.getName(), connector.getName());
            }
        }

        // removing phase
        Iterator<Connector2FunctionGroup> functionGroupsIterator = new HashSet<>(connector.getFunctionGroups()).iterator();
        while (functionGroupsIterator.hasNext()) {
            Connector2FunctionGroup c2fg = functionGroupsIterator.next();

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
            connector.getCredentials().stream().forEach(
                    c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!connector.getCaInstanceReferences().isEmpty()) {
            errors.add(ValidationError.create(
                    "Connector {} has {} dependent CA instances",
                    connector.getName(), connector.getCaInstanceReferences().size()));
            connector.getCaInstanceReferences().stream().forEach(
                    c -> errors.add(ValidationError.create(c.getName())));
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
    public List<ConnectDto> connect(ConnectorDto request) throws ValidationException, ConnectorException {
        return validateConnector(request);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.APPROVE)
    public void approve(List<String> uuids) throws NotFoundException, ValidationException {
        for(String uuid: uuids){
            try {
                Connector connector = connectorRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

                if (ConnectorStatus.WAITING_FOR_APPROVAL.equals(connector.getStatus())) {
                    connector.setStatus(ConnectorStatus.REGISTERED);
                } else {
                    logger.warn("Connector {} has unexpected status {}", connector.getName(), connector.getStatus());
                }
            }catch (NotFoundException e){
                logger.warn("Unable to find the connector with uuid {}", uuid);
            }
        }
    }


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CONNECT)
    public void reconnect(List<String> uuids) throws ValidationException, ConnectorException {
        for(String uuid: uuids){
            try {
                Connector connector = connectorRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

                List<ConnectDto> result = reValidateConnector(connector.mapToDto());

                List<FunctionGroupDto> functionGroups = result.stream()
                        .map(ConnectDto::getFunctionGroup)
                        .collect(Collectors.toList());

                setFunctionGroups(functionGroups, connector);
            }catch (NotFoundException e){
                logger.warn("Unable to find the connector with uuid {}", uuid);
            }
        }
    }


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.CONNECT)
    public List<ConnectDto> reconnect(String uuid) throws ValidationException, NotFoundException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        List<ConnectDto> result = reValidateConnector(connector.mapToDto());

        List<FunctionGroupDto> functionGroups = result.stream()
                .map(ConnectDto::getFunctionGroup)
                .collect(Collectors.toList());

        setFunctionGroups(functionGroups, connector);

        return result;
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

        for(BaseFunctionGroupDto f: functions){
            connectFunctionGroupKindMap.put(f.getFunctionGroupCode(), f.getKinds());
        }
        List<String> alreadyExistingConnector = new ArrayList<>();

        for(Connector connector: connectorRepository.findAll()){
            if(connector.getUuid().equals(uuid)){
                continue;
            }
            List<FunctionGroupCode> connectorFunctionGroups = connector.getFunctionGroups().stream().map(Connector2FunctionGroup::getFunctionGroup).collect(Collectors.toList()).stream().map(FunctionGroup::getCode).collect(Collectors.toList());
            if(connectFunctionGroupCodeList.equals(connectorFunctionGroups)){
                Map<FunctionGroupCode, List<String>> connectorFunctionGroupKindMap = new HashMap<>();

                for(Connector2FunctionGroup f: connector.getFunctionGroups()){
                    connectorFunctionGroupKindMap.put(f.getFunctionGroup().getCode(), MetaDefinitions.deserializeArrayString(f.getKinds()));
                }

                if(connectFunctionGroupKindMap.equals(connectorFunctionGroupKindMap)){
                    alreadyExistingConnector.add(connector.getName());
                }
            }
        }

        if(!alreadyExistingConnector.isEmpty()){
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
            connector.setStatus(ConnectorStatus.REGISTERED);
            connectorRepository.save(connector);
        } else {
            throw new ValidationException(ValidationError.create("Connector {} has unexpected status {}", connector.getName(), connector.getStatus()));
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.HEALTH, operation = OperationType.REQUEST)
    public HealthDto checkHealth(String uuid) throws NotFoundException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        return healthApiClient.checkHealth(connector.mapToDto());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> getAttributes(String uuid, FunctionGroupCode functionGroup, String functionGroupType) throws NotFoundException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        validateFunctionGroup(connector, functionGroup);

        return attributeApiClient.listAttributeDefinitions(connector.mapToDto(), functionGroup, functionGroupType);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public boolean validateAttributes(String uuid, FunctionGroupCode functionGroup, List<AttributeDefinition> attributes, String functionGroupType) throws NotFoundException, ValidationException, ConnectorException  {
        Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

        validateFunctionGroup(connector, functionGroup);

        return attributeApiClient.validateAttributes(connector.mapToDto(), functionGroup, attributes, functionGroupType);
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
	public Map<FunctionGroupCode, Map<String, List<AttributeDefinition>>> getAllAttributesOfConnector(String uuid) throws NotFoundException, ConnectorException {
		Connector connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

		Map<FunctionGroupCode, Map<String, List<AttributeDefinition>>> attributes = new HashMap<>();
		for(FunctionGroupDto fg: connector.mapToDto().getFunctionGroups()) {
			Map<String, List<AttributeDefinition>> kindsAttribute = new HashMap<>();
			for(String kind: fg.getKinds()) {
				kindsAttribute.put(kind, attributeApiClient.listAttributeDefinitions(connector.mapToDto(), fg.getFunctionGroupCode(), kind));
			}
			attributes.put(fg.getFunctionGroupCode(), kindsAttribute);
		}
		return attributes;
	}

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.CALLBACK)
    public Object callback(String uuid, AttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException {
        AttributeDefinitionUtils.validateCallback(callback);

        if (callback.getCallbackContext().equals("core/getCredentials")) {
            return coreCallbackService.coreGetCredentials(callback);
        }

        Connector connector = getConnectorEntity(uuid);

        // Load complete credential data for mapping of type credential
        credentialService.loadFullCredentialData(callback);

        return attributeApiClient.attributeCallback(connector.mapToDto(), callback);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.DELETE)
    public List<ForceDeleteMessageDto> bulkRemoveConnector(List<String> uuids) throws ValidationException, NotFoundException {
        List<Connector> deletableConnectors = new ArrayList<>();
        List<ForceDeleteMessageDto> messages = new ArrayList<>();
        for(String uuid: uuids) {
            List<String> errors = new ArrayList<>();
            Connector connector = connectorRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException(Connector.class, uuid));


            if (!connector.getCredentials().isEmpty()) {
                errors.add("Dependent Credentials: " + connector.getCredentials().size() + ". Names: ");
                connector.getCredentials().stream().forEach(
                        c -> errors.add(c.getName()));
            }

            if (!connector.getCaInstanceReferences().isEmpty()) {
                errors.add("CA instances: " + connector.getCaInstanceReferences().size() + ". Names: ");
                connector.getCaInstanceReferences().stream().forEach(
                        c -> errors.add(c.getName()));
            }

            if (!errors.isEmpty()) {
                ForceDeleteMessageDto forceModal = new ForceDeleteMessageDto();
                forceModal.setUuid(connector.getUuid());
                forceModal.setName(connector.getName());
                forceModal.setMessage(String.join(",",errors));
                messages.add(forceModal);
            }else {
                deletableConnectors.add(connector);
            }
        }
        for(Connector connector: deletableConnectors) {
            List<Connector2FunctionGroup> connector2FunctionGroups = connector2FunctionGroupRepository.findAllByConnector(connector);
            connector2FunctionGroupRepository.deleteAll(connector2FunctionGroups);
            connectorRepository.delete(connector);
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CONNECTOR, operation = OperationType.FORCE_DELETE)
    public void bulkForceRemoveConnector(List<String> uuids) throws ValidationException, NotFoundException {
        for(String uuid: uuids) {
            try {
                Connector connector = connectorRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Connector.class, uuid));

                if (!connector.getCredentials().isEmpty()) {
                    for (Credential credential : connector.getCredentials()) {
                        credential.setConnector(null);
                        credentialRepository.save(credential);
                        connector.getCredentials().remove(credential);
                        connectorRepository.save(connector);
                    }
                }

                if (!connector.getCaInstanceReferences().isEmpty()) {
                    for (CAInstanceReference ref : connector.getCaInstanceReferences()) {
                        ref.setConnector(null);
                        caInstanceReferenceRepository.save(ref);
                        connector.getCaInstanceReferences().remove(ref);
                        connectorRepository.save(connector);
                    }
                }
                List<Connector2FunctionGroup> connector2FunctionGroups = connector2FunctionGroupRepository.findAllByConnector(connector);
                connector2FunctionGroupRepository.deleteAll(connector2FunctionGroups);
                connectorRepository.delete(connector);
            } catch (NotFoundException e){
                logger.warn("Unable to find connector with uuid {}. It may have deleted already", uuid);
            }
        }
    }
}
