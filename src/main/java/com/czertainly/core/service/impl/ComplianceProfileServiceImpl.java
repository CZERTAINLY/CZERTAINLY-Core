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
    public ComplianceProfileDto getComplianceProfile(SecuredUUID uuid) {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
        ComplianceProfileDto dto = complianceProfile.mapToDto();
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.COMPLIANCE_PROFILE, uuid.getValue()));

        return dto;
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

        ComplianceProfileDto complianceProfileDto = updateComplianceProfileRules(complianceProfile, request);
        complianceProfileDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.COMPLIANCE_PROFILE, complianceProfile.getUuid(), request.getCustomAttributes()));

        return complianceProfileDto;
    }

    private ComplianceProfileDto updateComplianceProfileRules(ComplianceProfile complianceProfile, ComplianceProfileUpdateRequestDto request) throws ConnectorException, NotFoundException {
        ComplianceProfileDto complianceProfileDto = complianceProfile.mapToDto();

        // handle internal rules
        complianceProfileRuleRepository.deleteByComplianceProfileUuidAndInternalRuleUuidNotNull(complianceProfile.getUuid());
        for (UUID internalRuleUuid : request.getInternalRules()) {
            Rule internalRule = ruleRepository.findByUuid(internalRuleUuid).orElseThrow(() -> new NotFoundException("Internal rule", internalRuleUuid);
            if (internalRule.getResource() == Resource.ANY) {
                throw new ValidationException("Internal rule '%s' with ANY resource cannot be associated with compliance profile".formatted(internalRule.getName()));
            }

            ComplianceProfileRule profileRule = new ComplianceProfileRule();
            profileRule.setComplianceProfileUuid(complianceProfile.getUuid());
            profileRule.setInternalRuleUuid(internalRuleUuid);
            profileRule.setInternalRule(internalRule);
            profileRule.setResource(internalRule.getResource());
            complianceProfileRuleRepository.save(profileRule);
            complianceProfileDto.getInternalRules().add(internalRule.mapToComplianceRuleDto());
        }

        // handle providers rules
        for (ProviderComplianceRulesRequestDto providerRulesDto : request.getProviderRules()) {
            Map<UUID, ComplianceProfileRule> associatedProviderRules = new HashMap<>();
            Map<UUID, ComplianceProfileRule> associatedProviderGroups = new HashMap<>();
            Set<UUID> ruleUuids = providerRulesDto.getRules().stream().map(ComplianceRuleRequestDto::getUuid).collect(Collectors.toSet());
            ComplianceRulesGroupsBatchDto providerBatchDto = getComplianceProviderRulesBatch(providerRulesDto.getConnectorUuid(), providerRulesDto.getKind(), ruleUuids, providerRulesDto.getGroups(), false);
            List<ComplianceProfileRule> complianceProfileRules = complianceProfileRuleRepository.findByComplianceProfileUuidAndConnectorUuidAndKindAndInternalRuleUuidNull(complianceProfile.getUuid(), providerRulesDto.getConnectorUuid(), providerRulesDto.getKind());
            for (ComplianceProfileRule complianceProfileRule : complianceProfileRules) {
                if (complianceProfileRule.getComplianceRuleUuid() != null) {
                    associatedProviderRules.put(complianceProfileRule.getComplianceRuleUuid(), complianceProfileRule);
                } else {
                    associatedProviderGroups.put(complianceProfileRule.getComplianceGroupUuid(), complianceProfileRule);
                }
            }

            ProviderComplianceRulesDto providerComplianceRulesDto = new ProviderComplianceRulesDto();
            providerComplianceRulesDto.setConnectorUuid(providerBatchDto.getConnectorUuid());
            providerComplianceRulesDto.setConnectorName(providerBatchDto.getConnectorName());
            providerComplianceRulesDto.setKind(providerBatchDto.getKind());
            for (ComplianceRuleRequestDto providerRuleRequest : providerRulesDto.getRules()) {
                ComplianceRuleResponseDto providerRule = providerBatchDto.getRules().get(providerRuleRequest.getUuid());
                if (providerRule == null) {
                    throw new NotFoundException("Compliance rule with UUID %s not found in provider %s".formatted(providerRuleRequest.getUuid(), providerBatchDto.getConnectorName()));
                }

                // associate with compliance profile
                ComplianceRuleDto ruleDto = handleProviderRuleAssociation(complianceProfile.getUuid(), providerRuleRequest, providerRule, associatedProviderRules.get(providerRuleRequest.getUuid()));
                providerComplianceRulesDto.getRules().add(ruleDto);
            }

            for (UUID providerGroupUuid : providerRulesDto.getGroups()) {
                ComplianceGroupBatchResponseDto providerGroup = providerBatchDto.getGroups().get(providerGroupUuid);
                if (providerGroup == null) {
                    throw new NotFoundException("Compliance group with UUID %s not found in provider %s".formatted(providerGroupUuid, providerBatchDto.getConnectorName()));
                }

                // associate with compliance profile
                ComplianceGroupDto groupDto = handleProviderGroupAssociation(complianceProfile.getUuid(), providerGroup, associatedProviderGroups.get(providerGroupUuid));
                providerComplianceRulesDto.getGroups().add(groupDto);
            }
        }

        return complianceProfileDto;
    }

    private ComplianceRuleDto handleProviderRuleAssociation(UUID complianceProfileUuid, ComplianceRuleRequestDto providerRuleRequest, ComplianceRuleResponseDto providerRule, ComplianceProfileRule currentAssociation) {
        if (currentAssociation == null) {
            currentAssociation = new ComplianceProfileRule();
            currentAssociation.setComplianceProfileUuid(complianceProfileUuid);
            currentAssociation.setComplianceRuleUuid(providerRuleRequest.getUuid());
            currentAssociation.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.AVAILABLE);
            currentAssociation.setAttributes(providerRuleRequest.getAttributes());
        }
        complianceProfileRuleRepository.save(currentAssociation);

        return mapProviderRuleDto(currentAssociation, providerRule);
    }

    private ComplianceRuleDto mapProviderRuleDto(ComplianceProfileRule complianceProfileRule, ComplianceRuleResponseDto providerRule) throws ValidationException {
        StringBuilder updatedReason = new StringBuilder();

        ComplianceRuleDto ruleDto = new ComplianceRuleDto();
        ruleDto.setUuid(complianceProfileRule.getComplianceRuleUuid());
        ruleDto.setName(providerRule.getName());
        ruleDto.setDescription(providerRule.getDescription());
        ruleDto.setGroupUuid(providerRule.getGroupUuid());
        ruleDto.setResource(complianceProfileRule.getResource());
        ruleDto.setType(complianceProfileRule.getType());
        ruleDto.setFormat(providerRule.getFormat());

        // set other properties based on comparison, if not set before as newly created rule association
        if (complianceProfileRule.getAvailabilityStatus() == null) {
            if (complianceProfileRule.getResource() != providerRule.getResource()) {
                updatedReason.append("Resource changed from '%s' to '%s'\n".formatted(complianceProfileRule.getResource().getLabel(), providerRule.getResource().getLabel()));
            }
            if (!Objects.equals(complianceProfileRule.getType(), providerRule.getType())) {
                updatedReason.append("Resource type changed from '%s' to '%s'\n".formatted(Objects.toString(complianceProfileRule.getType(), "NULL"), Objects.toString(providerRule.getType(), "NULL")));
            }
            try {
                AttributeEngine.validateRequestDataAttributes(providerRule.getAttributes(), complianceProfileRule.getAttributes(), true);
            } catch (ValidationException e) {
                updatedReason.append("Rule attributes changed: %s\n".formatted(e.getMessage()));
            }
        } else {
            // for new rule association, just validate rule attributes
            AttributeEngine.validateRequestDataAttributes(providerRule.getAttributes(), complianceProfileRule.getAttributes(), true);
        }

        if (!updatedReason.isEmpty()) {
            ruleDto.setUpdatedReason(updatedReason.toString());
            ruleDto.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.UPDATED);
        } else {
            ruleDto.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.AVAILABLE);
        }
        ruleDto.setAttributes(AttributeEngine.getRequestDataAttributesContent(providerRule.getAttributes(), complianceProfileRule.getAttributes()));

        return ruleDto;
    }

    private ComplianceGroupDto handleProviderGroupAssociation(UUID complianceProfileUuid, ComplianceGroupBatchResponseDto providerGroup, ComplianceProfileRule currentAssociation) {
        if (currentAssociation == null) {
            currentAssociation = new ComplianceProfileRule();
            currentAssociation.setComplianceProfileUuid(complianceProfileUuid);
            currentAssociation.setComplianceGroupUuid(providerGroup.getUuid());
            currentAssociation.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.AVAILABLE);
        }
        complianceProfileRuleRepository.save(currentAssociation);

        return mapProviderGroupDto(currentAssociation, providerGroup);
    }

    private ComplianceGroupDto mapProviderGroupDto(ComplianceProfileRule complianceProfileRule, ComplianceGroupBatchResponseDto providerGroup) throws ValidationException {
        ComplianceGroupDto groupDto = new ComplianceGroupDto();
        groupDto.setUuid(complianceProfileRule.getComplianceRuleUuid());
        groupDto.setName(providerGroup.getName());
        groupDto.setDescription(providerGroup.getDescription());
        groupDto.setResource(complianceProfileRule.getResource());

        // set other properties based on comparison, if not set before as newly created rule association
        String updatedReason;
        if (complianceProfileRule.getAvailabilityStatus() == null) {
            if (complianceProfileRule.getResource() != providerGroup.getResource()) {
                groupDto.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.UPDATED);
                groupDto.setUpdatedReason("Resource changed from '%s' to '%s'".formatted(Objects.toString(complianceProfileRule.getResource().getLabel(), "NULL"), Objects.toString(providerGroup.getResource().getLabel(), "NULL")));
            } else {
                groupDto.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.AVAILABLE);
            }
        }

        return groupDto;
    }

    private ComplianceRulesGroupsBatchDto getComplianceProviderRulesBatch(UUID connectorUuid, String kind, Set<UUID> ruleUuids, Set<UUID> groupUuids, boolean withGroupRules) throws NotFoundException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(connectorUuid).orElseThrow(() -> new NotFoundException(Connector.class, connectorUuid));
        ConnectorDto connectorDto = connector.mapToDto();

        FunctionGroupDto functionGroup = connectorDto.getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER_V2)).findFirst().orElse(null);
        if (functionGroup == null) {
            functionGroup = connectorDto.getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().orElse(null);
        }
        if (functionGroup == null) {
            throw new ValidationException("Connector '%s' does not implement compliance function group".formatted(connector.getName()));
        }
        if (!functionGroup.getKinds().contains(kind)) {
            throw new ValidationException("Connector '%s' does not implement kind %s for function group '%s'".formatted(connector.getName(), kind, functionGroup.getFunctionGroupCode().getLabel()));
        }

        ComplianceRulesGroupsBatchDto rulesBatchDto = new ComplianceRulesGroupsBatchDto();
        rulesBatchDto.setConnectorUuid(connectorUuid);
        rulesBatchDto.setConnectorName(connectorDto.getName());
        rulesBatchDto.setKind(kind);

        if (functionGroup.getFunctionGroupCode() == FunctionGroupCode.COMPLIANCE_PROVIDER_V2) {
            ComplianceRulesBatchRequestDto rulesBatchRequestDto = new ComplianceRulesBatchRequestDto();
            rulesBatchRequestDto.setGroupUuids(groupUuids);
            rulesBatchRequestDto.setRuleUuids(ruleUuids);
            rulesBatchRequestDto.setWithGroupRules(withGroupRules);
            ComplianceRulesBatchResponseDto batchResponseDto = complianceApiClient.getComplianceRulesBatch(connectorDto, kind, rulesBatchRequestDto);

            rulesBatchDto.setRules(batchResponseDto.getRules().stream().collect(Collectors.toMap(ComplianceRuleResponseDto::getUuid, r -> r)));
            rulesBatchDto.setGroups(batchResponseDto.getGroups().stream().collect(Collectors.toMap(ComplianceGroupBatchResponseDto::getUuid, g -> g)));
        } else {
            getComplianceProviderV1RulesBatch(rulesBatchDto, connectorDto, kind, ruleUuids, groupUuids, withGroupRules);
        }

        return rulesBatchDto;
    }

    private void getComplianceProviderV1RulesBatch(ComplianceRulesGroupsBatchDto batchDto, ConnectorDto connectorDto, String kind, Set<UUID> ruleUuids, Set<UUID> groupUuids, boolean withGroupRules) throws ConnectorException, NotFoundException {
        batchDto.setGroups(complianceApiClientV1.getComplianceGroups(connectorDto, kind).stream().filter(g -> groupUuids.contains(UUID.fromString(g.getUuid()))).collect(Collectors.toMap(g -> UUID.fromString(g.getUuid()), g -> {
            var providerGroupBatchDto = new ComplianceGroupBatchResponseDto();
            providerGroupBatchDto.setUuid(UUID.fromString(g.getUuid()));
            providerGroupBatchDto.setName(g.getName());
            providerGroupBatchDto.setDescription(g.getDescription());
            providerGroupBatchDto.setResource(Resource.CERTIFICATE);
            return providerGroupBatchDto;
        })));

        var providerRules = complianceApiClientV1.getComplianceRules(connectorDto, kind, null);
        for (var providerRule : providerRules) {
            UUID providerRuleUuid = UUID.fromString(providerRule.getUuid());
            UUID providerRuleGroupUuid = NullUtil.parseUuidOrNull(providerRule.getGroupUuid());
            if (!ruleUuids.contains(providerRuleUuid) && (!withGroupRules || providerRuleGroupUuid == null || !groupUuids.contains(providerRuleGroupUuid))) {
                continue;
            }

            ComplianceRuleResponseDto ruleResponseDto = new ComplianceRuleResponseDto();
            ruleResponseDto.setUuid(providerRuleUuid);
            ruleResponseDto.setGroupUuid(providerRuleGroupUuid);
            ruleResponseDto.setName(providerRule.getName());
            ruleResponseDto.setDescription(providerRule.getDescription());
            ruleResponseDto.setResource(Resource.CERTIFICATE);
            ruleResponseDto.setType(providerRule.getCertificateType() != null ? providerRule.getCertificateType().getCode() : null);
            ruleResponseDto.setAttributes(providerRule.getAttributes());

            if (withGroupRules && providerRuleGroupUuid != null) {
                ComplianceGroupBatchResponseDto providerGroupBatchDto = batchDto.getGroups().get(providerRuleGroupUuid);
                if (providerGroupBatchDto != null) {
                    providerGroupBatchDto.getRules().add(ruleResponseDto);
                }
            }
            batchDto.getRules().put(providerRuleUuid, ruleResponseDto);
        }
    }

    @Override
    public ComplianceProfileDto updateComplianceProfile(SecuredUUID uuid, ComplianceProfileUpdateRequestDto request) {
        return null;
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
    public List<ComplianceRuleListDto> getComplianceRules(Resource resource, UUID connectorUuid, String kind, String type, String format) {
        return List.of();
    }

    @Override
    public List<ComplianceGroupListDto> getComplianceGroups(Resource resource, UUID connectorUuid, String kind) {
        return List.of();
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

    private ComplianceProfile deleteComplianceProfile(ComplianceProfile complianceProfile, boolean isForce) throws NotFoundException {
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
