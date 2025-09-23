package com.czertainly.core.model.compliance;

import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.connector.compliance.v2.ComplianceRulesBatchRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import com.czertainly.core.service.handler.ComplianceProfileRuleHandler;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class ComplianceCheckContext {

    private final Resource resource;
    private final String type;
    private final IPlatformEnum typeEnum;

    private final Map<UUID, ComplianceCheckProfileContext> profilesContextMap = new HashMap<>();
    private final Map<String, ComplianceCheckProviderContext> providersContextMap = new HashMap<>();

    private final Map<String, ComplianceRulesBatchRequestDto> providersBatchRequestMap = new HashMap<>();

    public ComplianceCheckContext(Resource resource, String type) {
        this.resource = resource;
        this.type = type;
        if (resource != null && type != null) {
            this.typeEnum = ComplianceProfileRuleHandler.getComplianceRuleTypeFromCode(resource, type);
        } else {
            this.typeEnum = null;
        }
    }

    public void addComplianceProfile(ComplianceProfile complianceProfile) {
        for (ComplianceProfileRule rule : complianceProfile.getComplianceRules()) {
            // skip rules that do not match the resource and type
            if ((resource != null && resource != rule.getResource()) || (typeEnum != null && !typeEnum.name().equals(rule.getType()))) {
                return;
            }

            // load compliance profile context
            ComplianceCheckProfileContext profileContext = profilesContextMap.computeIfAbsent(complianceProfile.getUuid(), uuid -> new ComplianceCheckProfileContext(complianceProfile.getUuid(), complianceProfile.getAssociations()));
            profileContext.addProfileRule(rule);

            // load compliance providers context
            if (rule.getInternalRuleUuid() != null) {
                String key = "%s|%s".formatted(rule.getConnectorUuid(), rule.getKind());
                ComplianceCheckProviderContext providerContext = providersContextMap.computeIfAbsent(key, k -> new ComplianceCheckProviderContext(rule.getConnectorUuid(), rule.getKind()));
                if (rule.getComplianceRuleUuid() != null) {
                    providerContext.getRulesBatchRequestDto().getRuleUuids().add(rule.getComplianceRuleUuid());
                } else {
                    providerContext.getRulesBatchRequestDto().getGroupUuids().add(rule.getComplianceGroupUuid());
                }
            }
        }
    }

}
