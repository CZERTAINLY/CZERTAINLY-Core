package com.czertainly.core.dao.entity;


import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.compliance.ComplianceRulesResponseDto;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entity containing the list of all discovered rules from a compliance provider. The rules are fetched from the compliance
 * provider, and are stored in the core. They are then used to map the rules for profiles. These rules are also tagged with the
 * group, if they belong to one.
 */
@Entity
@Table(name = "compliance_rule")
public class ComplianceRule extends UniquelyIdentified implements Serializable, DtoMapper<ComplianceRulesDto> {

    @Column(name = "name")
    private String name;

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", nullable = false, insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid", nullable = false)
    private UUID connectorUuid;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "group_uuid")
    private ComplianceGroup group;

    @Column(name = "group_uuid", insertable = false, updatable = false)
    private UUID groupUuid;

    @JsonBackReference
    @OneToMany(mappedBy = "complianceRule", fetch = FetchType.LAZY)
    private Set<ComplianceProfileRule> rules;

    @Override
    public ComplianceRulesDto mapToDto() {
        ComplianceRulesDto complianceRulesDto = new ComplianceRulesDto();
        complianceRulesDto.setName(name);
        complianceRulesDto.setUuid(uuid.toString());
        complianceRulesDto.setDescription(description);
        complianceRulesDto.setCertificateType(certificateType);
        complianceRulesDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(getAttributes()));
        return complianceRulesDto;
    }

    public ComplianceRulesResponseDto mapToComplianceResponse() {
        ComplianceRulesResponseDto dto = new ComplianceRulesResponseDto();
        dto.setName(name);
        dto.setUuid(uuid.toString());
        dto.setDescription(description);
        if (group != null) {
            dto.setGroupUuid(group.getUuid().toString());
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public List<BaseAttribute> getAttributes() {
        return AttributeDefinitionUtils.deserialize(attributes, BaseAttribute.class);
    }

    public void setAttributes(List<BaseAttribute> attributes) {
        this.attributes = AttributeDefinitionUtils.serialize(attributes);
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
        this.connectorUuid = connector.getUuid();
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
        if (group != null) this.groupUuid = group.getUuid();
        else this.groupUuid = null;
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

    public UUID getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(UUID connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public void setConnectorUuid(String connectorUuid) {
        this.connectorUuid = UUID.fromString(connectorUuid);
    }

    public UUID getGroupUuid() {
        return groupUuid;
    }

    public void setGroupUuid(String groupUuid) {
        this.groupUuid = UUID.fromString(groupUuid);
    }

    public void setGroupUuid(UUID groupUuid) {
        this.groupUuid = groupUuid;
    }
}
