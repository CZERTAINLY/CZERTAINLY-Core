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
import com.czertainly.core.service.ComplianceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ComplianceConnectorServiceImpl implements ComplianceConnectorService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceConnectorServiceImpl.class);

    @Autowired
    private ComplianceApiClient complianceApiClient;

    @Autowired
    @Lazy
    private ComplianceService complianceService;

    @Autowired
    private ComplianceGroupRepository complianceGroupRepository;

    @Autowired
    private ComplianceRuleRepository complianceRuleRepository;

    @Override
    public void addFetchGroupsAndRules(Connector connector) throws ConnectorException {
        logger.info("Fetching rules and groups for the Compliance Provider: {}", connector);
        FunctionGroupDto functionGroupDto = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().orElse(null);
        if (functionGroupDto == null) {
            logger.info("Connector: {} does not implement Compliance Provider", connector.getName());
            return;
        }
        for (String kind : functionGroupDto.getKinds()) {
            addGroups(connector, kind);
            addRules(connector, kind);
        }
    }

    @Override
    public void updateGroupsAndRules(Connector connector) throws ConnectorException {
        logger.info("Fetching the rules and groups of the Compliance Provider: {}", connector);
        FunctionGroupDto functionGroupDto = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().orElse(null);
        if (functionGroupDto == null) {
            logger.info("Connector: {} does not implement Compliance Provider", connector.getName());
            return;
        }
        for (String kind : functionGroupDto.getKinds()) {
            updateGroups(connector, kind);
            updateRules(connector, kind);
        }
    }

    private void addGroups(Connector connector, String kind) throws ConnectorException {
        logger.info("Adding groups for the Connector: {}, Kind: {}", connector.getName(), kind);
        List<ComplianceGroupsResponseDto> groups = complianceApiClient.getComplianceGroups(connector.mapToDto(), kind);
        logger.debug("Compliance Groups: {}", groups);
        List<String> groupUuids = groups.stream().map(ComplianceGroupsResponseDto::getUuid).collect(Collectors.toList());
        if (groupUuids.size() > new HashSet<>(groupUuids).size()) {
            logger.error("Duplicate UUIDs found from the connector: UUIDs: {}", groupUuids);
            throw new ValidationException(ValidationError.create("Compliance Groups from the connector contains duplicate UUIDs. UUIDs should be unique across the groups"));
        }
        for (ComplianceGroupsResponseDto group : groups) {
            logger.debug("Saving group: {}", group);
            complianceService.saveComplianceGroup(frameComplianceGroup(connector, kind, group));
        }
        logger.info("Groups for the connector: {} added", connector.getName());
    }

    private void addRules(Connector connector, String kind) throws ConnectorException {
        logger.info("Adding rules for the Connector: {}, Kind: {}", connector.getName(), kind);
        List<ComplianceRulesResponseDto> rules = complianceApiClient.getComplianceRules(connector.mapToDto(), kind, List.of());
        logger.debug("Compliance Groups: {}", rules);
        List<String> ruleUuids = rules.stream().map(ComplianceRulesResponseDto::getUuid).collect(Collectors.toList());
        if (ruleUuids.size() > new HashSet<>(ruleUuids).size()) {
            logger.error("Duplicate UUIDs found from the connector: UUIDs: {}", rules);
            throw new ValidationException(ValidationError.create("Compliance Rules from the connector contains duplicate UUIDs. UUIDs should be unique across the rules"));
        }
        for (ComplianceRulesResponseDto rule : rules) {
            logger.debug("Saving group: {}", rule);
            complianceService.saveComplianceRule(frameComplianceRule(connector, kind, rule));
        }
        logger.info("Rules for the connector: {} saved", connector.getName());
    }

    private ComplianceGroup frameComplianceGroup(Connector connector, String kind, ComplianceGroupsResponseDto group) {
        ComplianceGroup complianceGroup = new ComplianceGroup();
        complianceGroup.setConnector(connector);
        complianceGroup.setDescription(group.getDescription());
        complianceGroup.setKind(kind);
        complianceGroup.setName(group.getName());
        complianceGroup.setUuid(group.getUuid());
        complianceGroup.setDecommissioned(false);
        logger.debug("Compliance Group DAO: {}", complianceGroup);
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
        complianceRule.setAttributes(rule.getAttributes());
        if (rule.getGroupUuid() != null && !rule.getGroupUuid().isEmpty()) {
            if (complianceService.complianceGroupExists(rule.getGroupUuid(), connector, kind)) {
                complianceRule.setGroup(complianceService.getComplianceGroupEntity(rule.getGroupUuid(), connector, kind));
            } else {
                logger.warn("Compliance Rule: {}, tags unknown group:{}", rule.getUuid(), rule.getGroupUuid());
            }
        }
        logger.debug("Compliance Rule DAO: {}", complianceRule);
        return complianceRule;
    }

    private void updateGroups(Connector connector, String kind) throws ConnectorException {
        logger.info("Updating Compliance Group for: {}", connector);
        List<ComplianceGroupsResponseDto> groups = complianceApiClient.getComplianceGroups(connector.mapToDto(), kind);
        List<String> groupUuids = groups.stream().map(ComplianceGroupsResponseDto::getUuid).collect(Collectors.toList());
        decommUnavailableGroups(groupUuids, connector, kind);
        updateGroups(connector, kind, groups);

    }

    private void decommUnavailableGroups(List<String> groups, Connector connector, String kind) throws NotFoundException {
        logger.info("Preparing the decommision process for the groups that are removed from connector: {}", connector);
        List<String> currentGroupsInDatabase = complianceGroupRepository.findAll().stream().map(ComplianceGroup::getUuid).collect(Collectors.toList());
        currentGroupsInDatabase.removeAll(groups);
        for(String currentGroupUuid: currentGroupsInDatabase){
            ComplianceGroup complianceGroup = complianceService.getComplianceGroupEntity(currentGroupUuid, connector, kind);
            logger.debug("Group: {} no longer available", complianceGroup);
            complianceGroup.setDecommissioned(true);
            complianceService.saveComplianceGroup(complianceGroup);
        }
    }

    private void updateGroups(Connector connector, String kind, List<ComplianceGroupsResponseDto> groups) throws NotFoundException {
        for(ComplianceGroupsResponseDto group : groups){
            if(!complianceService.complianceGroupExists(group.getUuid(), connector, kind)){
                complianceService.saveComplianceGroup(frameComplianceGroup(connector, kind, group));
            }else{
                logger.debug("New group found. Adding group: {}", group);
                ComplianceGroup complianceGroup = complianceService.getComplianceGroupEntity(group.getUuid(), connector, kind);
                complianceGroup.setName(group.getName());
                complianceGroup.setDescription(group.getDescription());
                complianceService.saveComplianceGroup(complianceGroup);
            }
        }
    }

    private void updateRules(Connector connector, String kind) throws ConnectorException {
        logger.info("Updating Compliance Rules for: {}", connector);
        List<ComplianceRulesResponseDto> rules = complianceApiClient.getComplianceRules(connector.mapToDto(), kind, List.of());
        List<String> ruleUuids = rules.stream().map(ComplianceRulesResponseDto::getUuid).collect(Collectors.toList());
        decommUnavailableRules(ruleUuids, connector, kind);
        updateRules(connector, kind, rules);

    }

    private void decommUnavailableRules(List<String> rules, Connector connector, String kind) throws NotFoundException {
        logger.info("Preparing the decommision process for the rules that are removed from connector: {}", connector);
        List<String> currentRulesInDatabase = complianceRuleRepository.findAll().stream().map(ComplianceRule::getUuid).collect(Collectors.toList());
        currentRulesInDatabase.removeAll(rules);
        for(String currentRuleUuid: currentRulesInDatabase){
            ComplianceRule complianceRule = complianceService.getComplianceRuleEntity(currentRuleUuid, connector, kind);
            logger.debug("Rule: {} no longer available", complianceRule);
            complianceRule.setDecommissioned(true);
            complianceService.saveComplianceRule(complianceRule);
        }
    }

    private void updateRules(Connector connector, String kind, List<ComplianceRulesResponseDto> rules) throws NotFoundException {
        for(ComplianceRulesResponseDto rule : rules){
            if(!complianceService.complianceRuleExists(rule.getUuid(), connector, kind)){
                complianceService.saveComplianceRule(frameComplianceRule(connector, kind, rule));
            }else{
                ComplianceRule complianceRule = complianceService.getComplianceRuleEntity(rule.getUuid(), connector, kind);
                complianceRule.setName(rule.getName());
                complianceRule.setDescription(rule.getDescription());
                complianceRule.setCertificateType(rule.getCertificateType());
                if (rule.getGroupUuid() != null && !rule.getGroupUuid().isEmpty()) {
                    if (complianceService.complianceGroupExists(rule.getUuid(), connector, kind)) {
                        complianceRule.setGroup(complianceService.getComplianceGroupEntity(rule.getUuid(), connector, kind));
                    } else {
                        logger.warn("Compliance Rule: {}, tags unknown group:{}", rule.getUuid(), rule.getGroupUuid());
                    }
                }
                complianceService.saveComplianceRule(complianceRule);
            }
        }
    }

}
