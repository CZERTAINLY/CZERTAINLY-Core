package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.ComplianceRuleAvailabilityStatus;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.api.model.core.compliance.v2.BaseComplianceRuleDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceGroupDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceRuleDto;
import com.czertainly.core.dao.entity.workflows.Rule;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity containing the relation between the Compliance Profile and the Rule. This has to be maintained separately
 * considering that each rule can have its own attributes and values. And the values may be different for each compliance
 * profile. This entity frames the relation with the Compliance Profile and the Compliance Rules discovered from the
 * connector. In addition to that, it also stores the attributes needed for the rule
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "compliance_profile_rule")
public class ComplianceProfileRule extends UniquelyIdentified implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_profile_uuid", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private ComplianceProfile complianceProfile;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "kind")
    private String kind;

    @Column(name = "compliance_profile_uuid", nullable = false)
    private UUID complianceProfileUuid;

    @Column(name = "compliance_rule_uuid")
    private UUID complianceRuleUuid;

    @Column(name = "compliance_group_uuid")
    private UUID complianceGroupUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "internal_rule_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private Rule internalRule;

    @Column(name = "internal_rule_uuid")
    private UUID internalRuleUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false)
    private Resource resource;

    @Column(name = "type")
    private String type;

    @Column(name = "attributes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<RequestAttributeDto> attributes;

    @Transient
    private ComplianceRuleAvailabilityStatus availabilityStatus;

    public BaseComplianceRuleDto mapToDto() {
        if (complianceGroupUuid != null) {
            return complianceGroup.mapToDto(availabilityStatus);
        } else if (complianceRuleUuid != null) {
            return complianceRule.mapToDto(availabilityStatus);
        }

        return internalRule.mapToComplianceRuleDto();
    }

    public ComplianceRulesDto mapToDtoForProfile() {
        ComplianceRulesDto dto = new ComplianceRulesDto();
        dto.setName(complianceRule.getName());
        dto.setUuid(complianceRule.getUuid().toString());
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(getFullAttributes()));
        dto.setDescription(complianceRule.getDescription());
        return dto;
    }

    public void setComplianceRule(ComplianceRule complianceRule) {
        this.complianceRule = complianceRule;
        if (complianceRule != null) this.complianceRuleUuid = complianceRule.getUuid();
        else this.complianceRuleUuid = null;
    }

//    public List<DataAttribute> getFullAttributes() {
//        List<BaseAttribute> fullAttribute = complianceRule.getAttributes();
//        return AttributeDefinitionUtils.mergeAttributes(fullAttribute, AttributeDefinitionUtils.deserializeRequestAttributes(attributes));
//    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        ComplianceProfileRule that = (ComplianceProfileRule) o;
        return getUuid() != null && Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
