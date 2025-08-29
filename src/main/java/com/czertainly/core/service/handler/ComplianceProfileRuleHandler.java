package com.czertainly.core.service.handler;

import com.czertainly.api.clients.v2.ComplianceApiClient;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.repository.ComplianceGroupRepository;
import com.czertainly.core.dao.repository.ComplianceRuleRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.model.compliance.ComplianceRulesGroupsBatchDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Transactional
public class ComplianceProfileRuleHandler {

    private ComplianceApiClient complianceApiClient;
    private com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1;

    private ComplianceRuleRepository complianceRuleRepository;
    private ComplianceGroupRepository complianceGroupRepository;
    private ConnectorRepository connectorRepository;

    private AttributeEngine attributeEngine;

    public Map<UUID, ComplianceRule> loadComplianceRules(UUID connectorUuid, String kind, List<UUID> ruleUuids) {
        List<ComplianceRule> complianceRules = ruleUuids == null || ruleUuids.isEmpty()
                ? complianceRuleRepository.findByConnectorUuidAndKind(connectorUuid, kind)
                : complianceRuleRepository.findByConnectorUuidAndKindAndRuleUuidIn(connectorUuid, kind, ruleUuids);

        return complianceRules.stream().collect(Collectors.toMap(ComplianceRule::getRuleUuid, r -> r));
    }

    public Map<UUID, ComplianceGroup> loadComplianceGroups(UUID connectorUuid, String kind, List<UUID> groupUuids) {
        List<ComplianceGroup> complianceGroups = groupUuids == null || groupUuids.isEmpty()
                ? complianceGroupRepository.findByConnectorUuidAndKind(connectorUuid, kind)
                : complianceGroupRepository.findByConnectorUuidAndKindAndGroupUuidIn(connectorUuid, kind, groupUuids);

        return complianceGroups.stream().collect(Collectors.toMap(ComplianceGroup::getGroupUuid, g -> g));
    }



    public ComplianceProfileDto mapComplianceProfileDto(UUID complianceProfileUuid, ComplianceRulesGroupsBatchDto rulesBatchDto) {

    }
}
