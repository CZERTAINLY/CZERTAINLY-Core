package com.czertainly.core.service.impl;

import com.czertainly.api.clients.v2.ComplianceApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.compliance.v2.*;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.connector.compliance.v2.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.ComplianceRuleAvailabilityStatus;
import com.czertainly.api.model.core.compliance.v2.*;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.api.model.core.other.ResourceObjectDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.workflows.Rule;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.workflows.RuleRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.compliance.ComplianceRulesGroupsBatchDto;
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
import java.util.stream.Collectors;

@Service(Resource.Codes.COMPLIANCE_PROFILE)
@Transactional
public class ComplianceProfileServiceImpl implements ComplianceProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceProfileServiceImpl.class);

    private ComplianceApiClient complianceApiClient;
    private com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1;

    private ComplianceProfileRepository complianceProfileRepository;
    private ComplianceProfileRuleRepository complianceProfileRuleRepository;
    private ComplianceProfileAssociationRepository complianceProfileAssociationRepository;
    private ComplianceRuleRepository complianceRuleRepository;
    private ComplianceGroupRepository complianceGroupRepository;

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
    public void setComplianceRuleRepository(ComplianceRuleRepository complianceRuleRepository) {
        this.complianceRuleRepository = complianceRuleRepository;
    }

    @Autowired
    public void setComplianceGroupRepository(ComplianceGroupRepository complianceGroupRepository) {
        this.complianceGroupRepository = complianceGroupRepository;
    }

    @Autowired
    public void setComplianceProfileRuleRepository(ComplianceProfileRuleRepository complianceProfileRuleRepository) {
        this.complianceProfileRuleRepository = complianceProfileRuleRepository;
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
    public List<ComplianceRuleListDto> getComplianceRules(UUID connectorUuid, String kind, Resource resource, String type, String format) throws NotFoundException {


        Connector connector = connectorRepository.findByUuid(connectorUuid).orElseThrow(() -> new NotFoundException(Connector.class, connectorUuid));
        ConnectorDto connectorDto = connector.mapToDto();

        FunctionGroupCode complianceFunctionGroup = ruleHandler.validateComplianceProvider(connectorDto, kind);
        if(complianceFunctionGroup == FunctionGroupCode.COMPLIANCE_PROVIDER_V2) {
            return complianceApiClient.getComplianceRules(connectorDto, kind )
        }

    }

    @Override
    public List<ComplianceGroupListDto> getComplianceGroups(UUID connectorUuid, String kind, Resource resource) throws NotFoundException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(connectorUuid).orElseThrow(() -> new NotFoundException(Connector.class, connectorUuid));
        ConnectorDto connectorDto = connector.mapToDto();

        FunctionGroupCode complianceFunctionGroup = ruleHandler.validateComplianceProvider(connectorDto, kind);
        if(complianceFunctionGroup == FunctionGroupCode.COMPLIANCE_PROVIDER_V2) {
            return complianceApiClient.getComplianceGroups(connectorDto, kind, resource);
        }
    }

    @Override
    public void patchComplianceProfileRule(SecuredUUID uuid, ComplianceProfileRulesPatchRequestDto request) {

    }

    @Override
    public void patchComplianceProfileGroup(SecuredUUID uuid, ComplianceProfileGroupsPatchRequestDto request) {

    }

    @Override
    public List<ResourceObjectDto> getAssociations(SecuredUUID uuid) {
        return List.of();
    }

    @Override
    public void associateComplianceProfile(SecuredUUID uuid, Resource resource, UUID associationObjectUuid) {

    }

    @Override
    public void disassociateComplianceProfile(SecuredUUID uuid, Resource resource, UUID associationObjectUuid) {

    }

    @Override
    public List<ComplianceProfileListDto> getAssociatedComplianceProfiles(Resource resource, UUID associationObjectUuid) {
        return List.of();
    }

    @Override
    public void checkCompliance(List<SecuredUUID> uuids) {

    }

    @Override
    public void checkResourceObjectCompliance(Resource resource, UUID objectUuid) {

    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return List.of();
    }

    @Override
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {

    }

    private ComplianceProfile deleteComplianceProfile(ComplianceProfile complianceProfile, boolean isForce) {
        if (!isForce && !complianceProfile.getAssociations().isEmpty()) {
            var associations = getAssociations(complianceProfile.getSecuredUuid());
            String associatedObjects = String.join(", ", associations.stream().map(a -> "%s '%s'".formatted(a.getResource().getLabel(), a.getName())).toList());
            throw new ValidationException("Unable to delete compliance profile due to associated resource objects: " + associatedObjects);
        }

        complianceProfileAssociationRepository.deleteByComplianceProfileUuid(complianceProfile.getUuid());
        attributeEngine.deleteAllObjectAttributeContent(Resource.COMPLIANCE_PROFILE, complianceProfile.getUuid());

        complianceProfileRepository.delete(complianceProfile);

        return complianceProfile;
    }
}
