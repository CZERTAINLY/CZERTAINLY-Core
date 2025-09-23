package com.czertainly.core.model.compliance;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.ComplianceProfileAssociation;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import lombok.Getter;

import java.util.*;

@Getter
public class ComplianceCheckProfileContext {

    private final UUID complianceProfileUuid;

    private final Set<ComplianceProfileAssociation> associations;
    private final Map<Resource, List<Object>> complianceSubjects = new EnumMap<>(Resource.class);

    private final Map<UUID, ComplianceProfileRule> internalRulesMapping = new HashMap<>();
    private final Map<UUID, ComplianceProfileRule> rulesMapping = new HashMap<>();
    private final Map<UUID, ComplianceProfileRule> groupsMapping = new HashMap<>();

    public ComplianceCheckProfileContext(UUID complianceProfileUuid, Set<ComplianceProfileAssociation> associations) {
        this.complianceProfileUuid = complianceProfileUuid;
        this.associations = associations;
    }

    public void addProfileRule(ComplianceProfileRule rule) {
        if (rule.getInternalRuleUuid() != null) {
            internalRulesMapping.put(rule.getInternalRuleUuid(), rule);
        } else {
            if (rule.getComplianceRuleUuid() != null) {
                rulesMapping.put(rule.getComplianceRuleUuid(), rule);
            } else {
                groupsMapping.put(rule.getComplianceGroupUuid(), rule);
            }
        }
    }

}
