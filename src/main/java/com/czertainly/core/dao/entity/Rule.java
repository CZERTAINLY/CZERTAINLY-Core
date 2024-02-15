
package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "rule")
public class Rule extends UniquelyIdentified{

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "resource", nullable = false)
    private String resource;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_format")
    private String resourceFormat;

    @Column(name = "attributes")
    private String attributes;

    @OneToMany(mappedBy = "rule", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<RuleCondition> conditions;

    @OneToMany(mappedBy = "rule", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<RuleConditionGroup> conditionGroups;
}
