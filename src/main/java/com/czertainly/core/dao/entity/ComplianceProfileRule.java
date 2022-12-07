package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.compliance.ComplianceProfileRuleDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Entity containing the relation between the Compliance Profile and the Rule. This has to be maintained separately
 * considering that each rule can have its own attributes and values. And the values may be different for each compliance
 * profile. This entity frames the relation with the Compliance Profile and the Compliance Rules discovered from the
 * connector. In addition to that, it also stores the attributes needed for the rule
 */
@Entity
@Table(name = "compliance_profile_rule")
public class ComplianceProfileRule extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<ComplianceProfileRuleDto> {

    @OneToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "rule_uuid")
    private ComplianceRule complianceRule;

    @Column(name = "rule_uuid", insertable = false, updatable = false)
    private UUID complianceRuleUuid;

    @Column(name = "attributes")
    private String attributes;

    @OneToOne
    @JoinColumn(name = "compliance_profile_uuid")
    private ComplianceProfile complianceProfile;

    @Column(name = "compliance_profile_uuid", insertable = false, updatable = false)
    private UUID complianceProfileUuid;

    @Override
    public ComplianceProfileRuleDto mapToDto() {
        ComplianceProfileRuleDto dto = new ComplianceProfileRuleDto();
        dto.setName(complianceRule.getName());
        dto.setUuid(complianceRule.getUuid().toString());
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(getFullAttributes()));
        dto.setDescription(complianceRule.getDescription());
        dto.setComplianceProfileName(complianceProfile.getName());
        dto.setComplianceProfileUuid(complianceProfile.getUuid().toString());
        dto.setCertificateType(complianceRule.getCertificateType());
        dto.setConnectorUuid(complianceRule.getConnectorUuid().toString());
        dto.setConnectorName(complianceRule.getConnector().getName());
        dto.setGroupUuid(complianceRule.getGroupUuid() != null ? complianceRule.getGroupUuid().toString() : null);
        return dto;
    }

    public ComplianceRulesDto mapToDtoForProfile() {
        ComplianceRulesDto dto = new ComplianceRulesDto();
        dto.setName(complianceRule.getName());
        dto.setUuid(complianceRule.getUuid().toString());
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(getFullAttributes()));
        dto.setDescription(complianceRule.getDescription());
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("rule", complianceRule)
                .append("uuid", uuid)
                .append("attributes", attributes)
                .append("complianceProfile", complianceProfile)
                .toString();
    }

    public ComplianceRule getComplianceRule() {
        return complianceRule;
    }

    public void setComplianceRule(ComplianceRule complianceRule) {
        this.complianceRule = complianceRule;
        if (complianceRule != null) this.complianceRuleUuid = complianceRule.getUuid();
        else this.complianceRuleUuid = null;
    }

    public UUID getComplianceRuleUuid() {
        return complianceRuleUuid;
    }

    public void setComplianceRuleUuid(String complianceRuleUuid) {
        this.complianceRuleUuid = UUID.fromString(complianceRuleUuid);
    }

    public void setComplianceRuleUuid(UUID complianceRuleUuid) {
        this.complianceRuleUuid = complianceRuleUuid;
    }

    public List<RequestAttributeDto> getAttributes() {
        return AttributeDefinitionUtils.deserializeRequestAttributes(attributes);
    }

    public void setAttributes(List<RequestAttributeDto> attributes) {
        this.attributes = AttributeDefinitionUtils.serializeRequestAttributes(attributes);
    }

    public List<DataAttribute> getFullAttributes() {
        List<BaseAttribute> fullAttribute = complianceRule.getAttributes();
        return AttributeDefinitionUtils.mergeAttributes(fullAttribute, AttributeDefinitionUtils.deserializeRequestAttributes(attributes));
    }

    public ComplianceProfile getComplianceProfile() {
        return complianceProfile;
    }

    public void setComplianceProfile(ComplianceProfile complianceProfile) {
        this.complianceProfile = complianceProfile;
    }

    public UUID getComplianceProfileUuid() {
        return complianceProfileUuid;
    }

    public void setComplianceProfileUuid(String complianceProfileUuid) {
        this.complianceProfileUuid = UUID.fromString(complianceProfileUuid);
    }

    public void setComplianceProfileUuid(UUID complianceProfileUuid) {
        this.complianceProfileUuid = complianceProfileUuid;
    }
}
