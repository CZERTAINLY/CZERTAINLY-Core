package com.czertainly.core.dao.entity;


import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.compliance.ComplianceRulesResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceRuleAvailabilityStatus;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceRuleDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entity containing the list of all discovered rules from a compliance provider. The rules are fetched from the compliance
 * provider, and are stored in the core. They are then used to map the rules for profiles. These rules are also tagged with the
 * group, if they belong to one.
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "compliance_rule")
public class ComplianceRule extends UniquelyIdentified implements Serializable {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "rule_uuid", nullable = false)
    private UUID ruleUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false)
    private Resource resource;

    @Column(name = "type")
    private String type;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "kind")
    private String kind;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "group_uuid", insertable = false, updatable = false)
//    @ToString.Exclude
//    private ComplianceGroup group;

    @Column(name = "group_uuid")
    private UUID groupUuid;

    @Column(name = "attributes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<BaseAttribute> attributes;

    @JsonBackReference
    @OneToMany(mappedBy = "complianceRule", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<ComplianceProfileRule> complianceProfileRules;

    public ComplianceRuleDto mapToDto(ComplianceRuleAvailabilityStatus availabilityStatus) {
        ComplianceRuleDto dto = new ComplianceRuleDto();
        dto.setUuid(uuid);
        dto.setName(name);
        dto.setDescription(description);
        dto.setGroupUuid(groupUuid);
        dto.setAvailabilityStatus(availabilityStatus);
        dto.setResource(resource);
        dto.setType(type);
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(attributes));

        if (connectorUuid != null && connector != null) {
            dto.setKind(kind);
            dto.setConnectorUuid(connectorUuid);
            dto.setConnectorName(connector.getName());
        }

        return dto;
    }

    public ComplianceRulesDto mapToDtoV1() {
        ComplianceRulesDto complianceRulesDto = new ComplianceRulesDto();
        complianceRulesDto.setName(name);
        complianceRulesDto.setUuid(uuid.toString());
        complianceRulesDto.setDescription(description);
        complianceRulesDto.setCertificateType(CertificateType.X509);
        complianceRulesDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(getAttributes()));

        return complianceRulesDto;
    }

    public ComplianceRulesResponseDto mapToComplianceResponse() {
        ComplianceRulesResponseDto dto = new ComplianceRulesResponseDto();
        dto.setName(name);
        dto.setUuid(uuid.toString());
        dto.setDescription(description);
        if (groupUuid != null) {
            dto.setGroupUuid(groupUuid.toString());
        }
        dto.setCertificateType(CertificateType.X509);
        dto.setAttributes(getAttributes());
        return dto;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
        this.connectorUuid = connector.getUuid();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        ComplianceRule that = (ComplianceRule) o;
        return getUuid() != null && Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
