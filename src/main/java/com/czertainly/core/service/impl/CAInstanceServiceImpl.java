package com.czertainly.core.service.impl;

import com.czertainly.api.AttributeApiClient;
import com.czertainly.api.CAInstanceApiClient;
import com.czertainly.api.EndEntityProfileApiClient;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.NameAndIdDto;
import com.czertainly.api.model.ca.CAInstanceDto;
import com.czertainly.api.model.ca.CAInstanceRequestDto;
import com.czertainly.api.model.ca.ConnectorCAInstanceDto;
import com.czertainly.api.model.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.connector.FunctionGroupCode;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.CAInstanceReference;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CAInstanceReferenceRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.CAInstanceService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CoreCallbackService;
import com.czertainly.core.service.CredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class CAInstanceServiceImpl implements CAInstanceService {
    private static final Logger logger = LoggerFactory.getLogger(CAInstanceServiceImpl.class);

    @Autowired
    private CAInstanceReferenceRepository caInstanceReferenceRepository;
    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private CAInstanceApiClient caInstanceApiClient;
    @Autowired
    private EndEntityProfileApiClient endEntityProfileApiClient;
    @Autowired
    private AttributeApiClient attributeApiClient;
    @Autowired
    private CoreCallbackService coreCallbackService;
    @Autowired
    private RaProfileRepository raProfileRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    public List<CAInstanceDto> listCAInstances() {
        return caInstanceReferenceRepository.findAll().stream().map(CAInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    public CAInstanceDto getCAInstance(String uuid) throws NotFoundException, ConnectorException {
        CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));

        if (caInstanceRef.getConnector() == null) {
            throw new NotFoundException("Connector associated with the Authority is not found. Unable to show details");
        }

        ConnectorCAInstanceDto connectorCAInstanceDto = caInstanceApiClient.getCAInstance(caInstanceRef.getConnector().mapToDto(),
                caInstanceRef.getCaInstanceUuid());

        CAInstanceDto caInstanceDto = new CAInstanceDto();
        caInstanceDto.setAttributes(connectorCAInstanceDto.getAttributes());
        caInstanceDto.setName(connectorCAInstanceDto.getName());
        caInstanceDto.setUuid(caInstanceRef.getUuid());
        caInstanceDto.setConnectorUuid(caInstanceRef.getConnector().getUuid());
        caInstanceDto.setAuthorityType(caInstanceRef.getAuthorityType());
        caInstanceDto.setConnectorName(caInstanceRef.getConnectorName());

        return caInstanceDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CREATE)
    public CAInstanceDto createCAInstance(CAInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException {
        if (caInstanceReferenceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(CAInstanceReference.class, request.getName());
        }

        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());

        FunctionGroupCode codeToSearch = FunctionGroupCode.CA_CONNECTOR;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_CA_CONNECTOR) {
                codeToSearch = FunctionGroupCode.LEGACY_CA_CONNECTOR;
            }
        }
        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(connector.getUuid(), codeToSearch,
                request.getAttributes(), request.getAuthorityType());

        request.setAttributes(attributes); // todo - after split of CAInstanceDto, use attributes in core -> connector DTO

        // Load complete credential data
        credentialService.loadFullCredentialData(request.getAttributes());

        CAInstanceDto caInstanceDto = new CAInstanceDto();
        caInstanceDto.setConnectorName(connector.getName());
        caInstanceDto.setConnectorUuid(request.getConnectorUuid());
        caInstanceDto.setAttributes(request.getAttributes());
        caInstanceDto.setAuthorityType(request.getAuthorityType());
        caInstanceDto.setName(request.getName());

        ConnectorCAInstanceDto response = caInstanceApiClient.createCAInstance(connector.mapToDto(), caInstanceDto);

        CAInstanceReference caInstanceRef = new CAInstanceReference();
        caInstanceRef.setCaInstanceUuid(response.getUuid());
        caInstanceRef.setName(request.getName());
        caInstanceRef.setStatus("connected");
        caInstanceRef.setConnector(connector);
        caInstanceRef.setAuthorityType(request.getAuthorityType());
        caInstanceRef.setConnectorName(connector.getName());
        caInstanceReferenceRepository.save(caInstanceRef);

        return caInstanceRef.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CHANGE)
    public CAInstanceDto updateCAInstance(String uuid, CAInstanceRequestDto request) throws NotFoundException, ConnectorException {
        CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));

        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());

        FunctionGroupCode codeToSearch = FunctionGroupCode.CA_CONNECTOR;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_CA_CONNECTOR) {
                codeToSearch = FunctionGroupCode.LEGACY_CA_CONNECTOR;
            }
        }

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(connector.getUuid(), codeToSearch,
                request.getAttributes(), request.getAuthorityType());

        request.setAttributes(attributes); // todo - after split of CAInstanceDto, use attributes in core -> connector DTO

        // Load complete credential data
        credentialService.loadFullCredentialData(request.getAttributes());

        CAInstanceDto caInstanceDto = new CAInstanceDto();
        caInstanceDto.setConnectorName(connector.getName());
        caInstanceDto.setConnectorUuid(request.getConnectorUuid());
        caInstanceDto.setAttributes(request.getAttributes());
        caInstanceDto.setAuthorityType(request.getAuthorityType());
        caInstanceDto.setUuid(uuid);

        caInstanceApiClient.updateCAInstance(connector.mapToDto(),
                caInstanceRef.getCaInstanceUuid(), caInstanceDto);

        caInstanceRef.setAuthorityType(request.getAuthorityType());
        caInstanceRef.setConnector(connector);
        caInstanceRef.setConnectorName(connector.getName());
        caInstanceReferenceRepository.save(caInstanceRef);

        return caInstanceRef.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    public void removeCAInstance(String uuid) throws NotFoundException, ConnectorException {
        CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));

        List<ValidationError> errors = new ArrayList<>();
        if (!caInstanceRef.getRaProfiles().isEmpty()) {
            errors.add(ValidationError.create("CA instance {} has {} dependent RA profiles", caInstanceRef.getName(),
                    caInstanceRef.getRaProfiles().size()));
            caInstanceRef.getRaProfiles().stream().forEach(c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete CA instance", errors);
        }

        caInstanceApiClient.removeCAInstance(caInstanceRef.getConnector().mapToDto(), caInstanceRef.getCaInstanceUuid());

        caInstanceReferenceRepository.delete(caInstanceRef);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listEndEntityProfiles(String uuid) throws NotFoundException, ConnectorException {
        CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));

        return endEntityProfileApiClient.listEndEntityProfiles(caInstanceRef.getConnector().mapToDto(),
                caInstanceRef.getCaInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listCertificateProfiles(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));

        return endEntityProfileApiClient.listCertificateProfiles(caInstanceRef.getConnector().mapToDto(),
                caInstanceRef.getCaInstanceUuid(), endEntityProfileId);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listCAsInProfile(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));

        return endEntityProfileApiClient.listCAsInProfile(caInstanceRef.getConnector().mapToDto(),
                caInstanceRef.getCaInstanceUuid(), endEntityProfileId);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listRAProfileAttributes(String uuid) throws NotFoundException, ConnectorException {
        CAInstanceReference caInstance = caInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));
        Connector connector = caInstance.getConnector();

        return caInstanceApiClient.listRAProfileAttributes(connector.mapToDto(), caInstance.getCaInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public Boolean validateRAProfileAttributes(String uuid, List<AttributeDefinition> attributes) throws NotFoundException, ConnectorException {
        CAInstanceReference caInstance = caInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));
        Connector connector = caInstance.getConnector();

        return caInstanceApiClient.validateRAProfileAttributes(connector.mapToDto(), caInstance.getCaInstanceUuid(),
                attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    public List<ForceDeleteMessageDto> bulkRemoveCaInstance(List<String> uuids) throws NotFoundException, ValidationException, ConnectorException {
        List<CAInstanceReference> deletableCredentials = new ArrayList<>();
        List<ForceDeleteMessageDto> messages = new ArrayList<>();
        for (String uuid : uuids) {
            List<String> errors = new ArrayList<>();
            CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));

            if (!caInstanceRef.getRaProfiles().isEmpty()) {
                errors.add("RA Profiles: " + caInstanceRef.getRaProfiles().size() + ". Names: ");
                caInstanceRef.getRaProfiles().stream().forEach(c -> errors.add(c.getName()));
            }

            if (!errors.isEmpty()) {
                ForceDeleteMessageDto forceModal = new ForceDeleteMessageDto();
                forceModal.setUuid(caInstanceRef.getUuid());
                forceModal.setName(caInstanceRef.getName());
                forceModal.setMessage(String.join(",", errors));
                messages.add(forceModal);
            } else {
                deletableCredentials.add(caInstanceRef);
                try {
                    caInstanceApiClient.removeCAInstance(caInstanceRef.getConnector().mapToDto(), caInstanceRef.getCaInstanceUuid());
                }catch(ConnectorException e){
                    logger.error("Unable to delete authority with name {}", caInstanceRef.getName());
                }
            }
        }

        for (CAInstanceReference caInstanceRef : deletableCredentials) {
            caInstanceReferenceRepository.delete(caInstanceRef);
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.FORCE_DELETE)
    public void bulkForceRemoveCaInstance(List<String> uuids) throws ValidationException, NotFoundException {
        for (String uuid : uuids) {
            try{
            CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, uuid));
            if (!caInstanceRef.getRaProfiles().isEmpty()) {
                for(RaProfile ref: caInstanceRef.getRaProfiles()){
                    ref.setCaInstanceReference(null);
                    raProfileRepository.save(ref);
                }
            }
                caInstanceApiClient.removeCAInstance(caInstanceRef.getConnector().mapToDto(), caInstanceRef.getCaInstanceUuid());
            caInstanceReferenceRepository.delete(caInstanceRef);
        }catch (ConnectorException e){
                logger.warn("Unable to delete the ca Instance with uuid {}. It may have been deleted", uuid);
            }
        }
    }
}
