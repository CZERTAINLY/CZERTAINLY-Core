package com.czertainly.core.model.compliance;

import com.czertainly.api.model.connector.compliance.v2.ComplianceRulesBatchRequestDto;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ComplianceCheckProviderContext {

    private final UUID connectorUuid;
    private final String kind;

    private final ComplianceRulesBatchRequestDto rulesBatchRequestDto = new ComplianceRulesBatchRequestDto();
    private ComplianceRulesGroupsBatchDto rulesGroupsBatchDto;

    public ComplianceCheckProviderContext(UUID connectorUuid, String kind) {
        this.connectorUuid = connectorUuid;
        this.kind = kind;
    }

}
