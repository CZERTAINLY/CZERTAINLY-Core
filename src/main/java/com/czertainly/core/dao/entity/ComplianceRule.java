package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.connector.compliance.ComplianceRulesResponseDto;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * Entity containing the list of all discovered rules from a compliance provider. The rules are fetched from the compliance
 * provider, and are stored in the core. They are then used to map the rules for profiles. These rules are also tagged with the
 * group, if they belong to one.
 */
@Entity
@Table(name = "compliance_rule")
public class ComplianceRule implements Serializable, DtoMapper<ComplianceRulesDto> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "compliance_rule_seq")
    @SequenceGenerator(name = "compliance_rule_seq", sequenceName = "compliance_rule_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "kind")
    private String kind;

    @Column(name = "decommissioned")
    private Boolean decommissioned;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type")
    private CertificateType certificateType;

    @Column(name = "attributes")
    private String attributes;

    @Column(name = "description")
    private String description;

    @OneToOne
    @JoinColumn(name = "connector_id", nullable = false)
    private Connector connector;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private ComplianceGroup group;

    @JsonBackReference
    @OneToMany(mappedBy = "complianceRule")
    private Set<ComplianceProfileRule> rules;

    @Override
    public ComplianceRulesDto mapToDto(){
        ComplianceRulesDto complianceRulesDto = new ComplianceRulesDto();
        complianceRulesDto.setName(name);
        complianceRulesDto.setUuid(uuid);
        complianceRulesDto.setDescription(description);
        complianceRulesDto.setCertificateType(certificateType);
        complianceRulesDto.setAttributes(AttributeDefinitionUtils.deserializeRequestAttributes(attributes));
        return complianceRulesDto;
    }

    public ComplianceRulesResponseDto mapToComplianceResponse(){
        ComplianceRulesResponseDto dto = new ComplianceRulesResponseDto();
        dto.setName(name);
        dto.setUuid(uuid);
        dto.setDescription(description);
        if(group != null ) {
            dto.setGroupUuid(group.getUuid());
        }
        dto.setCertificateType(certificateType);
        dto.setAttributes(getAttributes());
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("name", name)
                .append("attributes", attributes)
                .append("certificateType", certificateType)
                .append("description", description)
                .append("kind", kind)
                .append("complianceRule", rules)
                .append("certificateType", certificateType)
                .append("decommissioned", decommissioned)
                .toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public List<AttributeDefinition> getAttributes() {
        return AttributeDefinitionUtils.deserialize(attributes);
    }

    public void setAttributes(List<AttributeDefinition> attributes) {
        this.attributes = AttributeDefinitionUtils.serialize(attributes);
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CertificateType getCertificateType() {
        return certificateType;
    }

    public void setCertificateType(CertificateType certificateType) {
        this.certificateType = certificateType;
    }

    public ComplianceGroup getGroup() {
        return group;
    }

    public void setGroup(ComplianceGroup group) {
        this.group = group;
    }

    public Set<ComplianceProfileRule> getRules() {
        return rules;
    }

    public void setRules(Set<ComplianceProfileRule> rules) {
        this.rules = rules;
    }

    public Boolean getDecommissioned() {
        return decommissioned;
    }

    public void setDecommissioned(Boolean decommissioned) {
        this.decommissioned = decommissioned;
    }
}
