package com.czertainly.core.dao.entity;

import com.czertainly.api.model.connector.compliance.ComplianceGroupsResponseDto;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Set;

/**
 * Entity storing the available groups obtained from the Compliance Provider. These entities have relations with the
 * respective connector mapped to the connector table. If the user chooses to associate a group to a compliance profile,
 * a foreign key relation of type manyToMany will be established using the table compliance_group_to_compliance_profile
 */
@Entity
@Table(name = "compliance_group")
public class ComplianceGroup extends UniquelyIdentified implements Serializable {

    @Column(name = "name")
    private String name;

    @Column(name = "kind")
    private String kind;

    @Column(name = "description")
    private String description;

    @Column(name="decommissioned")
    private Boolean decommissioned;

    @OneToOne
    @JoinColumn(name = "connector_uuid", nullable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private String connectorUuid;

    @JsonBackReference
    @OneToMany(mappedBy = "group")
    private Set<ComplianceRule> rules;

    @JsonBackReference
    @ManyToMany(mappedBy = "groups")
    private Set<ComplianceProfile> complianceProfiles;

    public ComplianceGroupsResponseDto mapToGroupResponse(){
        ComplianceGroupsResponseDto dto = new ComplianceGroupsResponseDto();
        dto.setUuid(uuid);
        dto.setName(name);
        dto.setDescription(description);
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("name", name)
                .append("kind", kind)
                .append("description", description)
                .append("decommissioned", decommissioned)
                .toString();
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

    public Set<ComplianceRule> getRules() {
        return rules;
    }

    public void setRules(Set<ComplianceRule> rules) {
        this.rules = rules;
    }

    public Boolean getDecommissioned() {
        return decommissioned;
    }

    public void setDecommissioned(Boolean decommissioned) {
        this.decommissioned = decommissioned;
    }

    public Set<ComplianceProfile> getComplianceProfiles() {
        return complianceProfiles;
    }

    public void setComplianceProfiles(Set<ComplianceProfile> complianceProfiles) {
        this.complianceProfiles = complianceProfiles;
    }
}
