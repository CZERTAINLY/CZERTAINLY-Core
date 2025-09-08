package com.czertainly.core.service.impl;

import com.czertainly.api.clients.v2.ComplianceApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.compliance.v2.*;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.connector.compliance.ComplianceGroupsResponseDto;
import com.czertainly.api.model.connector.compliance.ComplianceRulesResponseDto;
import com.czertainly.api.model.connector.compliance.v2.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.v2.*;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.other.ResourceObjectDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.workflows.Rule;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.workflows.RuleRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.service.handler.ComplianceProfileRuleHandler;
import com.czertainly.core.util.NullUtil;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service(Resource.Codes.COMPLIANCE_PROFILE)
@Transactional
public class ComplianceProfileServiceImpl implements ComplianceProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceProfileServiceImpl.class);

    private ComplianceApiClient complianceApiClient;
    private com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1;

    private ComplianceProfileRepository complianceProfileRepository;
    private ComplianceProfileRuleRepository complianceProfileRuleRepository;
    private ComplianceProfileAssociationRepository complianceProfileAssociationRepository;

    private ResourceService resourceService;
    private ConnectorRepository connectorRepository;
    private RuleRepository ruleRepository;

    private AttributeEngine attributeEngine;
    private ComplianceProfileRuleHandler ruleHandler;

    @Autowired
    public void setComplianceApiClient(ComplianceApiClient complianceApiClient) {
        this.complianceApiClient = complianceApiClient;
    }

    @Autowired
    public void setComplianceApiClientV1(com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1) {
        this.complianceApiClientV1 = complianceApiClientV1;
    }

    @Autowired
    public void setComplianceProfileRepository(ComplianceProfileRepository complianceProfileRepository) {
        this.complianceProfileRepository = complianceProfileRepository;
    }

    @Autowired
    public void setComplianceProfileAssociationRepository(ComplianceProfileAssociationRepository complianceProfileAssociationRepository) {
        this.complianceProfileAssociationRepository = complianceProfileAssociationRepository;
    }

    @Autowired
    public void setComplianceProfileRuleRepository(ComplianceProfileRuleRepository complianceProfileRuleRepository) {
        this.complianceProfileRuleRepository = complianceProfileRuleRepository;
    }

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Autowired
    public void setConnectorRepository(ConnectorRepository connectorRepository) {
        this.connectorRepository = connectorRepository;
    }

    @Autowired
    public void setRuleRepository(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setRuleHandler(ComplianceProfileRuleHandler ruleHandler) {
        this.ruleHandler = ruleHandler;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<ComplianceProfileListDto> listComplianceProfiles(SecurityFilter filter) {
        return complianceProfileRepository.findUsingSecurityFilter(filter).stream().map(ComplianceProfile::mapToListDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public ComplianceProfileDto getComplianceProfile(SecuredUUID uuid) throws NotFoundException, ConnectorException {
        return ruleHandler.mapComplianceProfileDto(uuid.getValue());
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CREATE)
    public ComplianceProfileDto createComplianceProfile(ComplianceProfileRequestDto request) throws AlreadyExistException, ConnectorException, NotFoundException, AttributeException {
        if (complianceProfileRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(ComplianceProfile.class, request.getName());
        }
        attributeEngine.validateCustomAttributesContent(Resource.COMPLIANCE_PROFILE, request.getCustomAttributes());

        ComplianceProfile complianceProfile = new ComplianceProfile();
        complianceProfile.setName(request.getName());
        complianceProfile.setDescription(request.getDescription());
        complianceProfile = complianceProfileRepository.save(complianceProfile);

        ComplianceProfileDto complianceProfileDto = ruleHandler.updateComplianceProfileRules(complianceProfile, request);
        complianceProfileDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.COMPLIANCE_PROFILE, complianceProfile.getUuid(), request.getCustomAttributes()));

        return complianceProfileDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public ComplianceProfileDto updateComplianceProfile(SecuredUUID uuid, ComplianceProfileUpdateRequestDto request) throws NotFoundException, ConnectorException, AttributeException {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
        attributeEngine.validateCustomAttributesContent(Resource.COMPLIANCE_PROFILE, request.getCustomAttributes());
        if (!Objects.equals(complianceProfile.getDescription(), request.getDescription())) {
            complianceProfile.setDescription(request.getDescription());
            complianceProfile = complianceProfileRepository.save(complianceProfile);
        }

        ComplianceProfileDto complianceProfileDto = ruleHandler.updateComplianceProfileRules(complianceProfile, request);
        complianceProfileDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.COMPLIANCE_PROFILE, complianceProfile.getUuid(), request.getCustomAttributes()));

        return complianceProfileDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DELETE)
    public void deleteComplianceProfile(SecuredUUID uuid) throws NotFoundException {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
        deleteComplianceProfile(complianceProfile, false);
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> bulkDeleteComplianceProfiles(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            logger.debug("Deleting compliance profile with UUID: {}", uuid);
            ComplianceProfile complianceProfile = null;
            try {
                complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
                deleteComplianceProfile(complianceProfile, false);
            } catch (Exception e) {
                logger.warn("Unable to delete the Compliance Profile with UUID {}: {}", uuid, e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), complianceProfile != null ? complianceProfile.getName() : "", e.getMessage()));
            }
        }

        return messages;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> forceDeleteComplianceProfiles(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            logger.debug("Force deleting compliance profile with UUID: {}", uuid);
            ComplianceProfile complianceProfile = null;
            try {
                complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
                deleteComplianceProfile(complianceProfile, true);
            } catch (Exception e) {
                logger.warn("Unable to force delete the Compliance Profile with UUID {}: {}", uuid, e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), complianceProfile != null ? complianceProfile.getName() : "", e.getMessage()));
            }
        }

        return messages;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<ComplianceRuleListDto> getComplianceRules(UUID connectorUuid, String kind, Resource resource, String type, String format) throws NotFoundException, ConnectorException {
        if (resource == Resource.ANY || resource == Resource.NONE) {
            throw new ValidationException("Cannot list compliance rules for resource %s".formatted(resource.getLabel()));
        }

        // load internal rules if no connector uuid is specified
        List<ComplianceRuleListDto> complianceRules = new ArrayList<>();
        if (connectorUuid == null) {
            List<Rule> internalRules = ruleRepository.findAllByResource(resource);
            for (Rule internalRule : internalRules) {
                complianceRules.add(internalRule.mapToComplianceRuleListDto());
            }
            return complianceRules;
        }

        Connector connector = connectorRepository.findByUuid(connectorUuid).orElseThrow(() -> new NotFoundException(Connector.class, connectorUuid));
        ConnectorDto connectorDto = connector.mapToDto();
        FunctionGroupCode complianceFunctionGroup = ruleHandler.validateComplianceProvider(connectorDto, kind);
        if (complianceFunctionGroup == FunctionGroupCode.COMPLIANCE_PROVIDER_V2) {
            List<ComplianceRuleResponseDto> providerRules = resource == null ? complianceApiClient.getComplianceRules(connectorDto, kind, null, null, null) : complianceApiClient.getComplianceRules(connectorDto, kind, resource, type, format);
            for (ComplianceRuleResponseDto providerRule : providerRules) {
                ComplianceRuleListDto dto = new ComplianceRuleListDto();
                dto.setUuid(providerRule.getUuid());
                dto.setName(providerRule.getName());
                dto.setDescription(providerRule.getDescription());
                dto.setConnectorUuid(connectorUuid);
                dto.setKind(kind);
                dto.setGroupUuid(providerRule.getGroupUuid());
                dto.setResource(providerRule.getResource());
                dto.setType(providerRule.getType());
                dto.setFormat(providerRule.getFormat());
                dto.setAttributes(providerRule.getAttributes());
                complianceRules.add(dto);
            }
        } else {
            if (resource != null && resource != Resource.CERTIFICATE) {
                return List.of();
            }
            List<ComplianceRulesResponseDto> providerRules = complianceApiClientV1.getComplianceRules(connectorDto, kind, List.of(type));
            for (ComplianceRulesResponseDto providerRule : providerRules) {
                ComplianceRuleListDto dto = new ComplianceRuleListDto();
                dto.setUuid(UUID.fromString(providerRule.getUuid()));
                dto.setName(providerRule.getName());
                dto.setDescription(providerRule.getDescription());
                dto.setConnectorUuid(connectorUuid);
                dto.setKind(kind);
                dto.setGroupUuid(NullUtil.parseUuidOrNull(providerRule.getGroupUuid()));
                dto.setResource(Resource.CERTIFICATE);
                dto.setAttributes(providerRule.getAttributes());
                complianceRules.add(dto);
            }
        }

        return complianceRules;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<ComplianceGroupListDto> getComplianceGroups(UUID connectorUuid, String kind, Resource resource) throws NotFoundException, ConnectorException {
        List<ComplianceGroupListDto> complianceGroups = new ArrayList<>();

        Connector connector = connectorRepository.findByUuid(connectorUuid).orElseThrow(() -> new NotFoundException(Connector.class, connectorUuid));
        ConnectorDto connectorDto = connector.mapToDto();
        FunctionGroupCode complianceFunctionGroup = ruleHandler.validateComplianceProvider(connectorDto, kind);
        if (complianceFunctionGroup == FunctionGroupCode.COMPLIANCE_PROVIDER_V2) {
            List<ComplianceGroupResponseDto> providerGroups = complianceApiClient.getComplianceGroups(connectorDto, kind, resource);
            for (ComplianceGroupResponseDto providerGroup : providerGroups) {
                ComplianceGroupListDto dto = new ComplianceGroupListDto();
                dto.setUuid(providerGroup.getUuid());
                dto.setName(providerGroup.getName());
                dto.setDescription(providerGroup.getDescription());
                dto.setConnectorUuid(connectorUuid);
                dto.setKind(kind);
                dto.setResource(providerGroup.getResource());
                complianceGroups.add(dto);
            }
        } else {
            List<ComplianceGroupsResponseDto> providerGroups = complianceApiClientV1.getComplianceGroups(connectorDto, kind);
            for (ComplianceGroupsResponseDto providerGroup : providerGroups) {
                ComplianceGroupListDto dto = new ComplianceGroupListDto();
                dto.setUuid(UUID.fromString(providerGroup.getUuid()));
                dto.setName(providerGroup.getName());
                dto.setDescription(providerGroup.getDescription());
                dto.setConnectorUuid(connectorUuid);
                dto.setKind(kind);
                dto.setResource(Resource.CERTIFICATE);
                complianceGroups.add(dto);
            }
        }

        return complianceGroups;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<ComplianceRuleListDto> getComplianceGroupRules(UUID groupUuid, UUID connectorUuid, String kind) throws NotFoundException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(connectorUuid).orElseThrow(() -> new NotFoundException(Connector.class, connectorUuid));
        ConnectorDto connectorDto = connector.mapToDto();
        FunctionGroupCode complianceFunctionGroup = ruleHandler.validateComplianceProvider(connectorDto, kind);
        if (complianceFunctionGroup == FunctionGroupCode.COMPLIANCE_PROVIDER_V2) {
            return complianceApiClient.getComplianceGroupRules(connectorDto, kind, groupUuid).stream().map(providerRule -> {
                ComplianceRuleListDto dto = new ComplianceRuleListDto();
                dto.setUuid(providerRule.getUuid());
                dto.setName(providerRule.getName());
                dto.setDescription(providerRule.getDescription());
                dto.setConnectorUuid(connectorUuid);
                dto.setKind(kind);
                dto.setGroupUuid(providerRule.getGroupUuid());
                dto.setResource(providerRule.getResource());
                dto.setType(providerRule.getType());
                dto.setFormat(providerRule.getFormat());
                dto.setAttributes(providerRule.getAttributes());
                return dto;
            }).toList();
        }

        return complianceApiClientV1.getComplianceGroupRules(connectorDto, kind, groupUuid.toString()).stream().map(providerRule -> {
            ComplianceRuleListDto dto = new ComplianceRuleListDto();
            dto.setUuid(UUID.fromString(providerRule.getUuid()));
            dto.setName(providerRule.getName());
            dto.setDescription(providerRule.getDescription());
            dto.setConnectorUuid(connectorUuid);
            dto.setKind(kind);
            dto.setGroupUuid(NullUtil.parseUuidOrNull(providerRule.getGroupUuid()));
            dto.setResource(Resource.CERTIFICATE);
            dto.setAttributes(providerRule.getAttributes());
            return dto;
        }).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public void patchComplianceProfileRule(SecuredUUID uuid, ComplianceProfileRulesPatchRequestDto request) throws NotFoundException, ConnectorException {
        if (!complianceProfileRepository.existsById(uuid.getValue())) {
            throw new NotFoundException(ComplianceProfile.class, uuid.getValue());
        }

        if (request.getConnectorUuid() == null) {
            // internal rule
            complianceProfileRuleRepository.deleteByComplianceProfileUuidAndInternalRuleUuid(uuid.getValue(), request.getRuleUuid());
            if (!request.isRemoval()) {
                ruleHandler.createComplianceProfileInternalRuleAssoc(uuid.getValue(), request.getRuleUuid());
            }
        } else {
            // provider rule
            complianceProfileRuleRepository.deleteByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceRuleUuid(uuid.getValue(), request.getConnectorUuid(), request.getKind(), request.getRuleUuid());
            if (!request.isRemoval()) {
                ComplianceRuleResponseDto providerRule = ruleHandler.getProviderRule(request.getConnectorUuid(), request.getKind(), request.getRuleUuid());
                ruleHandler.createComplianceProfileProviderRuleAssoc(uuid.getValue(), providerRule, request.getAttributes());
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public void patchComplianceProfileGroup(SecuredUUID uuid, ComplianceProfileGroupsPatchRequestDto request) throws ConnectorException, NotFoundException {
        if (!complianceProfileRepository.existsById(uuid.getValue())) {
            throw new NotFoundException(ComplianceProfile.class, uuid.getValue());
        }

        complianceProfileRuleRepository.deleteByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceGroupUuid(uuid.getValue(), request.getConnectorUuid(), request.getKind(), request.getGroupUuid());
        if (!request.isRemoval()) {
            ComplianceGroupResponseDto providerGroup = ruleHandler.getProviderGroup(request.getConnectorUuid(), request.getKind(), request.getGroupUuid());
            ruleHandler.createComplianceProfileProviderGroupAssoc(uuid.getValue(), providerGroup);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public List<ResourceObjectDto> getAssociations(SecuredUUID uuid) throws NotFoundException {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));

        List<ResourceObjectDto> associatedObjects = new ArrayList<>();
        for (ComplianceProfileAssociation association : complianceProfile.getAssociations()) {
            associatedObjects.add(resourceService.getResourceObject(association.getResource(), association.getObjectUuid()));
        }

        return associatedObjects;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public List<ComplianceProfileListDto> getAssociatedComplianceProfiles(Resource resource, UUID associationObjectUuid) {
        List<ComplianceProfileAssociation> associations = complianceProfileAssociationRepository.findByResourceAndObjectUuid(resource, associationObjectUuid);

        return associations.stream().map(a -> a.getComplianceProfile().mapToListDto()).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public void associateComplianceProfile(SecuredUUID uuid, Resource resource, UUID associationObjectUuid) throws NotFoundException {
        if (!complianceProfileRepository.existsById(uuid.getValue())) {
            throw new NotFoundException(ComplianceProfile.class, uuid.getValue());
        }

        if (!complianceProfileAssociationRepository.existsByComplianceProfileUuidAndResourceAndObjectUuid(uuid.getValue(), resource, associationObjectUuid)) {
            ComplianceProfileAssociation association = new ComplianceProfileAssociation();
            association.setComplianceProfileUuid(uuid.getValue());
            association.setResource(resource);
            association.setObjectUuid(associationObjectUuid);
            complianceProfileAssociationRepository.save(association);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public void disassociateComplianceProfile(SecuredUUID uuid, Resource resource, UUID associationObjectUuid) throws NotFoundException {
        if (!complianceProfileRepository.existsById(uuid.getValue())) {
            throw new NotFoundException(ComplianceProfile.class, uuid.getValue());
        }

        complianceProfileAssociationRepository.deleteByComplianceProfileUuidAndResourceAndObjectUuid(uuid.getValue(), resource, associationObjectUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkCompliance(List<SecuredUUID> uuids) {

    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectCompliance(Resource resource, UUID objectUuid) {

    }

    @Override
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(objectUuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, objectUuid));
        return new NameAndUuidDto(complianceProfile.getUuid().toString(), complianceProfile.getName());
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return complianceProfileRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(ComplianceProfile::mapToAccessControlObjects).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
    }

    private void deleteComplianceProfile(ComplianceProfile complianceProfile, boolean isForce) throws NotFoundException {
        if (!isForce && !complianceProfile.getAssociations().isEmpty()) {
            var associations = getAssociations(complianceProfile.getSecuredUuid());
            String associatedObjects = String.join(", ", associations.stream().map(a -> "%s '%s'".formatted(a.getResource().getLabel(), a.getName())).toList());
            throw new ValidationException("Unable to delete compliance profile due to associated resource objects: " + associatedObjects);
        }

        complianceProfileAssociationRepository.deleteByComplianceProfileUuid(complianceProfile.getUuid());
        attributeEngine.deleteAllObjectAttributeContent(Resource.COMPLIANCE_PROFILE, complianceProfile.getUuid());

        complianceProfileRepository.delete(complianceProfile);
    }
}
