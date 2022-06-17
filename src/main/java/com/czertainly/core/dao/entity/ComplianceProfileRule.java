package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.compliance.*;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

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

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private List<RequestAttributeDto> attributes;

    @OneToOne
    @JoinColumn(name = "rule_id")
    private ComplianceProfile complianceProfile;

    @Override
    public ComplianceRulesDto mapToDto(){
        ComplianceRulesDto dto = new ComplianceRulesDto();
        dto.setName(complianceRule.getName());
        dto.setUuid(complianceRule.getUuid());
        dto.setAttributes(attributes);
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
        return attributes;
    }

    public void setAttributes(List<RequestAttributeDto> attributes) {
        this.attributes = attributes;
    }

    public ComplianceProfile getComplianceProfile() {
        return complianceProfile;
    }

    public void setComplianceProfile(ComplianceProfile complianceProfile) {
        this.complianceProfile = complianceProfile;
    }
}
