package com.czertainly.core.service.handler;

import com.czertainly.api.clients.v2.ComplianceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileUpdateRequestDto;
import com.czertainly.api.model.client.compliance.v2.ProviderComplianceRulesRequestDto;
import com.czertainly.api.model.connector.compliance.v2.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.ComplianceRuleAvailabilityStatus;
import com.czertainly.api.model.core.compliance.v2.ComplianceGroupDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceRuleDto;
import com.czertainly.api.model.core.compliance.v2.ProviderComplianceRulesDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.workflows.Rule;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.workflows.RuleRepository;
import com.czertainly.core.model.compliance.ComplianceRulesGroupsBatchDto;
import com.czertainly.core.util.NullUtil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Transactional
public class ComplianceProfileRuleHandler {

    private ComplianceApiClient complianceApiClient;
    private com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1;

    private ConnectorRepository connectorRepository;
    private RuleRepository ruleRepository;
    private ComplianceProfileRepository complianceProfileRepository;
    private ComplianceProfileRuleRepository complianceProfileRuleRepository;

    private AttributeEngine attributeEngine;

    public ComplianceProfileDto mapComplianceProfileDto(UUID complianceProfileUuid) throws NotFoundException, ConnectorException {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(complianceProfileUuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, complianceProfileUuid));
        ComplianceProfileDto dto = complianceProfile.mapToDto();

        Map<String, List<ComplianceProfileRule>> profileRulesMapping = new HashMap<>();
        Map<String, List<ComplianceProfileRule>> profileGroupsMapping = new HashMap<>();
        Map<String, ComplianceRulesBatchRequestDto> providerRulesRequestMapping = new HashMap<>();
        for (ComplianceProfileRule complianceProfileRule : complianceProfile.getComplianceRules()) {
            if (complianceProfileRule.getInternalRuleUuid() != null) {
                dto.getInternalRules().add(complianceProfileRule.getInternalRule().mapToComplianceRuleDto());
            } else {
                String key = "%s|%s".formatted(complianceProfileRule.getConnectorUuid(), complianceProfileRule.getKind());

                ComplianceRulesBatchRequestDto providerRulesRequest = providerRulesRequestMapping.get(key);
                if (providerRulesRequest == null) {
                    providerRulesRequest = new ComplianceRulesBatchRequestDto();
                    providerRulesRequestMapping.put(key, providerRulesRequest);

                    profileRulesMapping.put(key, new ArrayList<>());
                    profileGroupsMapping.put(key, new ArrayList<>());
                }

                if (complianceProfileRule.getComplianceRuleUuid() != null) {
                    providerRulesRequest.getRuleUuids().add(complianceProfileRule.getComplianceRuleUuid());
                    profileRulesMapping.get(key).add(complianceProfileRule);
                } else {
                    providerRulesRequest.getGroupUuids().add(complianceProfileRule.getComplianceGroupUuid());
                    profileGroupsMapping.get(key).add(complianceProfileRule);
                }
            }
        }

        for (var entry : providerRulesRequestMapping.entrySet()) {
            String[] keyParts = entry.getKey().split("\\|");
            ComplianceRulesGroupsBatchDto providerRulesBatch = getComplianceProviderRulesBatch(UUID.fromString(keyParts[0]), keyParts[1], entry.getValue().getRuleUuids(), entry.getValue().getGroupUuids(), false);

            ProviderComplianceRulesDto providerComplianceRulesDto = new ProviderComplianceRulesDto();
            providerComplianceRulesDto.setConnectorUuid(providerRulesBatch.getConnectorUuid());
            providerComplianceRulesDto.setConnectorName(providerRulesBatch.getConnectorName());
            providerComplianceRulesDto.setKind(providerRulesBatch.getKind());

            // map rules to DTO
            for (ComplianceProfileRule complianceProfileRule : profileRulesMapping.get(entry.getKey())) {
                ComplianceRuleResponseDto providerRule = providerRulesBatch.getRules().get(complianceProfileRule.getComplianceRuleUuid());
                providerComplianceRulesDto.getRules().add(mapProviderRuleDto(complianceProfileRule, providerRule));
            }

            // map groups to DTO
            for (ComplianceProfileRule complianceProfileRule : profileGroupsMapping.get(entry.getKey())) {
                ComplianceGroupBatchResponseDto providerGroup = providerRulesBatch.getGroups().get(complianceProfileRule.getComplianceGroupUuid());
                providerComplianceRulesDto.getGroups().add(mapProviderGroupDto(complianceProfileRule, providerGroup));
            }

            dto.getProviderRules().add(providerComplianceRulesDto);
        }

        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.COMPLIANCE_PROFILE, complianceProfileUuid));

        return dto;
    }

    private ComplianceRuleDto mapProviderRuleDto(ComplianceProfileRule complianceProfileRule, ComplianceRuleResponseDto providerRule) throws ValidationException {
        ComplianceRuleDto ruleDto = new ComplianceRuleDto();
        ruleDto.setUuid(complianceProfileRule.getComplianceRuleUuid());
        ruleDto.setResource(complianceProfileRule.getResource());
        ruleDto.setType(complianceProfileRule.getType());

        if (providerRule == null) {
            ruleDto.setName("N/A");
            ruleDto.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.NOT_AVAILABLE);
            return ruleDto;
        }

        ruleDto.setName(providerRule.getName());
        ruleDto.setDescription(providerRule.getDescription());
        ruleDto.setGroupUuid(providerRule.getGroupUuid());
        ruleDto.setFormat(providerRule.getFormat());

        // set other properties based on comparison, if not set before as newly created rule association
        StringBuilder updatedReason = new StringBuilder();
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

    private ComplianceGroupDto mapProviderGroupDto(ComplianceProfileRule complianceProfileRule, ComplianceGroupBatchResponseDto providerGroup) throws ValidationException {
        ComplianceGroupDto groupDto = new ComplianceGroupDto();
        groupDto.setUuid(complianceProfileRule.getComplianceGroupUuid());
        groupDto.setResource(complianceProfileRule.getResource());

        if (providerGroup == null) {
            groupDto.setName("N/A");
            groupDto.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.NOT_AVAILABLE);
            return groupDto;
        }

        groupDto.setName(providerGroup.getName());
        groupDto.setDescription(providerGroup.getDescription());

        // set other properties based on comparison, if not set before as newly created rule association
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

    public ComplianceProfileDto updateComplianceProfileRules(ComplianceProfile complianceProfile, ComplianceProfileUpdateRequestDto request) throws ConnectorException, NotFoundException {
        ComplianceProfileDto complianceProfileDto = complianceProfile.mapToDto();

        // delete all compliance rules associations
        complianceProfileRuleRepository.deleteByComplianceProfileUuid(complianceProfile.getUuid());

        // handle internal rules
        for (UUID internalRuleUuid : request.getInternalRules()) {
            Rule internalRule = ruleRepository.findByUuid(internalRuleUuid).orElseThrow(() -> new NotFoundException("Internal rule", internalRuleUuid));
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
//            Map<UUID, ComplianceProfileRule> associatedProviderRules = new HashMap<>();
//            Map<UUID, ComplianceProfileRule> associatedProviderGroups = new HashMap<>();
            Set<UUID> ruleUuids = providerRulesDto.getRules().stream().map(ComplianceRuleRequestDto::getUuid).collect(Collectors.toSet());
            ComplianceRulesGroupsBatchDto providerBatchDto = getComplianceProviderRulesBatch(providerRulesDto.getConnectorUuid(), providerRulesDto.getKind(), ruleUuids, providerRulesDto.getGroups(), false);
//            List<ComplianceProfileRule> complianceProfileRules = complianceProfileRuleRepository.findByComplianceProfileUuidAndConnectorUuidAndKindAndInternalRuleUuidNull(complianceProfile.getUuid(), providerRulesDto.getConnectorUuid(), providerRulesDto.getKind());
//            for (ComplianceProfileRule complianceProfileRule : complianceProfileRules) {
//                if (complianceProfileRule.getComplianceRuleUuid() != null) {
//                    associatedProviderRules.put(complianceProfileRule.getComplianceRuleUuid(), complianceProfileRule);
//                } else {
//                    associatedProviderGroups.put(complianceProfileRule.getComplianceGroupUuid(), complianceProfileRule);
//                }
//            }

            ProviderComplianceRulesDto providerComplianceRulesDto = new ProviderComplianceRulesDto();
            providerComplianceRulesDto.setConnectorUuid(providerBatchDto.getConnectorUuid());
            providerComplianceRulesDto.setConnectorName(providerBatchDto.getConnectorName());
            providerComplianceRulesDto.setKind(providerBatchDto.getKind());
            for (ComplianceRuleRequestDto providerRuleRequest : providerRulesDto.getRules()) {
                ComplianceRuleResponseDto providerRule = providerBatchDto.getRules().get(providerRuleRequest.getUuid());
                if (providerRule == null) {
                    throw new NotFoundException("Compliance rule with UUID %s not found in provider %s".formatted(providerRuleRequest.getUuid(), providerBatchDto.getConnectorName()));
                }

                // associate provider rule with compliance profile
                ComplianceProfileRule complianceProfileRule = new ComplianceProfileRule();
                complianceProfileRule.setComplianceProfileUuid(complianceProfile.getUuid());
                complianceProfileRule.setComplianceRuleUuid(providerRuleRequest.getUuid());
                complianceProfileRule.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.AVAILABLE);
                complianceProfileRule.setAttributes(providerRuleRequest.getAttributes());
                complianceProfileRuleRepository.save(complianceProfileRule);

                ComplianceRuleDto ruleDto = mapProviderRuleDto(complianceProfileRule, providerRule);
                providerComplianceRulesDto.getRules().add(ruleDto);
            }

            for (UUID providerGroupUuid : providerRulesDto.getGroups()) {
                ComplianceGroupBatchResponseDto providerGroup = providerBatchDto.getGroups().get(providerGroupUuid);
                if (providerGroup == null) {
                    throw new NotFoundException("Compliance group with UUID %s not found in provider %s".formatted(providerGroupUuid, providerBatchDto.getConnectorName()));
                }

                // associate provider group with compliance profile
                ComplianceProfileRule complianceProfileRule = new ComplianceProfileRule();
                complianceProfileRule.setComplianceProfileUuid(complianceProfile.getUuid());
                complianceProfileRule.setComplianceGroupUuid(providerGroup.getUuid());
                complianceProfileRule.setAvailabilityStatus(ComplianceRuleAvailabilityStatus.AVAILABLE);
                complianceProfileRuleRepository.save(complianceProfileRule);

                ComplianceGroupDto groupDto = mapProviderGroupDto(complianceProfileRule, providerGroup);
                providerComplianceRulesDto.getGroups().add(groupDto);
            }
        }

        return complianceProfileDto;
    }

    private ComplianceRulesGroupsBatchDto getComplianceProviderRulesBatch(UUID connectorUuid, String kind, Set<UUID> ruleUuids, Set<UUID> groupUuids, boolean withGroupRules) throws NotFoundException, ConnectorException {
        Connector connector = connectorRepository.findByUuid(connectorUuid).orElseThrow(() -> new NotFoundException(Connector.class, connectorUuid));
        ConnectorDto connectorDto = connector.mapToDto();

        FunctionGroupCode functionGroup = validateComplianceProvider(connectorDto, kind);

        ComplianceRulesGroupsBatchDto rulesBatchDto = new ComplianceRulesGroupsBatchDto();
        rulesBatchDto.setConnectorUuid(connectorUuid);
        rulesBatchDto.setConnectorName(connectorDto.getName());
        rulesBatchDto.setKind(kind);
        if (functionGroup == FunctionGroupCode.COMPLIANCE_PROVIDER_V2) {
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

    public FunctionGroupCode validateComplianceProvider(ConnectorDto connectorDto, String kind) throws ValidationException {
        FunctionGroupDto functionGroup = connectorDto.getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER_V2)).findFirst().orElse(null);
        if (functionGroup == null) {
            functionGroup = connectorDto.getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().orElse(null);
        }
        if (functionGroup == null) {
            throw new ValidationException("Connector '%s' does not implement compliance function group".formatted(connectorDto.getName()));
        }
        if (!functionGroup.getKinds().contains(kind)) {
            throw new ValidationException("Connector '%s' does not implement kind %s for function group '%s'".formatted(connectorDto.getName(), kind, functionGroup.getFunctionGroupCode().getLabel()));
        }

        return functionGroup.getFunctionGroupCode();
    }
}
