package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

/**
 * Entity containing the relation between the Compliance Profile and the Rule. This has to be maintained separately
 * considering that each rule can have its own attributes and values. And the values may be different for each compliance
 * profile. This entity frames the relation with the Compliance Profile and the Compliance Rules discovered from the
 * connector. In addition to that, it also stores the attributes needed for the rule
 */
@Entity
@Table(name = "compliance_profile_rule")
public class ComplianceProfileRule extends Audited implements Serializable, DtoMapper<ComplianceRulesDto> {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "compliance_profile_rule_seq")
    @SequenceGenerator(name = "compliance_profile_rule_seq", sequenceName = "compliance_profile_rule_id_seq", allocationSize = 1)
    private Long id;

    @OneToOne
    @JoinColumn(name = "rule_id")
    private ComplianceRule complianceRule;

    @Column(name="attributes")
    private String attributes;

    @OneToOne
    @JoinColumn(name = "compliance_profile_id")
    private ComplianceProfile complianceProfile;

    @Override
    public ComplianceRulesDto mapToDto(){
        ComplianceRulesDto dto = new ComplianceRulesDto();
        dto.setName(complianceRule.getName());
        dto.setUuid(complianceRule.getUuid());
        dto.setAttributes(getAttributes());
        dto.setDescription(complianceProfile.getDescription());
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("rule", complianceRule)
                .append("attributes", attributes)
                .append("complianceProfile", complianceProfile)
                .toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ComplianceRule getComplianceRule() {
        return complianceRule;
    }

    public void setComplianceRule(ComplianceRule complianceRule) {
        this.complianceRule = complianceRule;
    }

    public List<RequestAttributeDto> getAttributes() {
        return AttributeDefinitionUtils.deserializeRequestAttributes(attributes);
    }

    public void setAttributes(List<RequestAttributeDto> attributes) {
        this.attributes = AttributeDefinitionUtils.serializeRequestAttributes(attributes);
    }

    public ComplianceProfile getComplianceProfile() {
        return complianceProfile;
    }

    public void setComplianceProfile(ComplianceProfile complianceProfile) {
        this.complianceProfile = complianceProfile;
    }
}
