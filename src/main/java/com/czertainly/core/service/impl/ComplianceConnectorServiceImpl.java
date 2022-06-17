package com.czertainly.core.service.impl;

import com.czertainly.api.clients.ComplianceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.connector.compliance.ComplianceGroupsResponseDto;
import com.czertainly.api.model.connector.compliance.ComplianceRulesResponseDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.ComplianceGroupRepository;
import com.czertainly.core.dao.repository.ComplianceRuleRepository;
import com.czertainly.core.service.ComplianceConnectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class ComplianceConnectorServiceImpl implements ComplianceConnectorService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceConnectorServiceImpl.class);

    @Autowired
    private ComplianceApiClient complianceApiClient;

    @Autowired
    private ComplianceGroupRepository complianceGroupRepository;

    @Autowired
    private ComplianceRuleRepository complianceRuleRepository;

    @Override
    public void addFetchGroupsAndRules(Connector connector) throws ConnectorException {
        FunctionGroupDto functionGroupDto = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().orElse(null);
        if (functionGroupDto == null) {
            logger.info("Connector: {} does not implement Compliance Provider", connector.getName());
        }
        for (String kind : functionGroupDto.getKinds()) {
            addGroups(connector, kind);
            addRules(connector, kind);
        }
    }

    @Override
    public void updateGroupsAndRules(Connector connector) throws ConnectorException {
        FunctionGroupDto functionGroupDto = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().orElse(null);
        if (functionGroupDto == null) {
            logger.info("Connector: {} does not implement Compliance Provider", connector.getName());
        }
        for (String kind : functionGroupDto.getKinds()) {
            updateGroups(connector, kind);
            updateRules(connector, kind);
        }
    }

    private void addGroups(Connector connector, String kind) throws ConnectorException {
        List<ComplianceGroupsResponseDto> groups = complianceApiClient.getComplianceGroups(connector.mapToDto(), kind);
        List<String> groupUuids = groups.stream().map(ComplianceGroupsResponseDto::getUuid).collect(Collectors.toList());
        if (groupUuids.size() > new HashSet<>(groupUuids).size()) {
            throw new ValidationException(ValidationError.create("Compliance Groups from the connector contains duplicate UUIDs. UUIDs should be unique across the groups"));
        }
        for (ComplianceGroupsResponseDto group : groups) {
            complianceGroupRepository.save(frameComplianceGroup(connector, kind, group));
        }
    }

    private void addRules(Connector connector, String kind) throws ConnectorException {
        List<ComplianceRulesResponseDto> rules = complianceApiClient.getComplianceRules(connector.mapToDto(), kind, List.of());
        List<String> ruleUuids = rules.stream().map(ComplianceRulesResponseDto::getUuid).collect(Collectors.toList());
        if (ruleUuids.size() > new HashSet<>(ruleUuids).size()) {
            throw new ValidationException(ValidationError.create("Compliance Rules from the connector contains duplicate UUIDs. UUIDs should be unique across the rules"));
        }
        for (ComplianceRulesResponseDto rule : rules) {
            complianceRuleRepository.save(frameComplianceRule(connector, kind, rule));
        }
    }

    private ComplianceGroup frameComplianceGroup(Connector connector, String kind, ComplianceGroupsResponseDto group) {
        ComplianceGroup complianceGroup = new ComplianceGroup();
        complianceGroup.setConnector(connector);
        complianceGroup.setDescription(group.getDescription());
        complianceGroup.setKind(kind);
        complianceGroup.setName(group.getName());
        complianceGroup.setUuid(group.getUuid());
        complianceGroup.setDecommissioned(false);
        return complianceGroup;
    }

    private ComplianceRule frameComplianceRule(Connector connector, String kind, ComplianceRulesResponseDto rule) throws NotFoundException {
        ComplianceRule complianceRule = new ComplianceRule();
        complianceRule.setConnector(connector);
        complianceRule.setDescription(rule.getDescription());
        complianceRule.setKind(kind);
        complianceRule.setName(rule.getName());
        complianceRule.setUuid(rule.getUuid());
        complianceRule.setDecommissioned(false);
        complianceRule.setCertificateType(rule.getCertificateType());
        if (rule.getGroupUuid() != null && !rule.getGroupUuid().isEmpty()) {
            if (complianceGroupExists(rule.getUuid(), connector, kind)) {
                complianceRule.setGroup(getComplianceGroupEntity(rule.getUuid(), connector, kind));
            } else {
                logger.warn("Compliance Rule: {}, tags unknown group:{}", rule.getUuid(), rule.getGroupUuid());
            }
        }
        return complianceRule;
    }

    private ComplianceGroup getComplianceGroupEntity(String uuid, Connector connector, String kind) throws NotFoundException {
        return complianceGroupRepository.findByUuidAndConnectorAndKind(uuid, connector, kind).orElseThrow(() -> new NotFoundException(ComplianceGroup.class, uuid));
    }

    private Boolean complianceGroupExists(String uuid, Connector connector, String kind) {
        return complianceGroupRepository.findByUuidAndConnectorAndKind(uuid, connector, kind).isPresent();
    }

    private ComplianceRule getComplianceRuleEntity(String uuid, Connector connector, String kind) throws NotFoundException {
        return complianceRuleRepository.findByUuidAndConnectorAndKind(uuid, connector, kind).orElseThrow(() -> new NotFoundException(ComplianceRule.class, uuid));
    }

    private Boolean complianceRuleExists(String uuid, Connector connector, String kind) {
        return complianceRuleRepository.findByUuidAndConnectorAndKind(uuid, connector, kind).isPresent();
    }

    private void updateGroups(Connector connector, String kind) throws ConnectorException {
        List<ComplianceGroupsResponseDto> groups = complianceApiClient.getComplianceGroups(connector.mapToDto(), kind);
        List<String> groupUuids = groups.stream().map(ComplianceGroupsResponseDto::getUuid).collect(Collectors.toList());
        decommUnavailableGroups(groupUuids, connector, kind);
        updateGroups(connector, kind, groups);

    }

    private void decommUnavailableGroups(List<String> groups, Connector connector, String kind) throws NotFoundException {
        List<String> currentGroupsInDatabase = complianceGroupRepository.findAll().stream().map(ComplianceGroup::getUuid).collect(Collectors.toList());
        currentGroupsInDatabase.removeAll(groups);
        for(String currentGroupUuid: currentGroupsInDatabase){
            ComplianceGroup complianceGroup = getComplianceGroupEntity(currentGroupUuid, connector, kind);
            complianceGroup.setDecommissioned(true);
            complianceGroupRepository.save(complianceGroup);
        }
    }

    private void updateGroups(Connector connector, String kind, List<ComplianceGroupsResponseDto> groups) throws NotFoundException {
        for(ComplianceGroupsResponseDto group : groups){
            if(!complianceGroupExists(group.getUuid(), connector, kind)){
                complianceGroupRepository.save(frameComplianceGroup(connector, kind, group));
            }else{
                ComplianceGroup complianceGroup = getComplianceGroupEntity(group.getUuid(), connector, kind);
                complianceGroup.setName(group.getName());
                complianceGroup.setDescription(group.getDescription());
                complianceGroupRepository.save(complianceGroup);
            }
        }
    }

    private void updateRules(Connector connector, String kind) throws ConnectorException {
        List<ComplianceRulesResponseDto> rules = complianceApiClient.getComplianceRules(connector.mapToDto(), kind, List.of());
        List<String> ruleUuids = rules.stream().map(ComplianceRulesResponseDto::getUuid).collect(Collectors.toList());
        decommUnavailableRules(ruleUuids, connector, kind);
        updateRules(connector, kind, rules);

    }

    private void decommUnavailableRules(List<String> rules, Connector connector, String kind) throws NotFoundException {
        List<String> currentRulesInDatabase = complianceRuleRepository.findAll().stream().map(ComplianceRule::getUuid).collect(Collectors.toList());
        currentRulesInDatabase.removeAll(rules);
        for(String currentRuleUuid: currentRulesInDatabase){
            ComplianceRule complianceRule = getComplianceRuleEntity(currentRuleUuid, connector, kind);
            complianceRule.setDecommissioned(true);
            complianceRuleRepository.save(complianceRule);
        }
    }

    private void updateRules(Connector connector, String kind, List<ComplianceRulesResponseDto> rules) throws NotFoundException {
        for(ComplianceRulesResponseDto rule : rules){
            if(!complianceRuleExists(rule.getUuid(), connector, kind)){
                complianceRuleRepository.save(frameComplianceRule(connector, kind, rule));
            }else{
                ComplianceRule complianceRule = getComplianceRuleEntity(rule.getUuid(), connector, kind);
                complianceRule.setName(rule.getName());
                complianceRule.setDescription(rule.getDescription());
                complianceRule.setCertificateType(rule.getCertificateType());
                if (rule.getGroupUuid() != null && !rule.getGroupUuid().isEmpty()) {
                    if (complianceGroupExists(rule.getUuid(), connector, kind)) {
                        complianceRule.setGroup(getComplianceGroupEntity(rule.getUuid(), connector, kind));
                    } else {
                        logger.warn("Compliance Rule: {}, tags unknown group:{}", rule.getUuid(), rule.getGroupUuid());
                    }
                }
                complianceRuleRepository.save(complianceRule);
            }
        }
    }

}
