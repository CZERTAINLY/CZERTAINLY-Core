package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.search.SearchCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "rule_action")
public class RuleAction extends UniquelyIdentified{

    @Column(name = "action_group_uuid")
    private UUID conditionGroupUuid;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "search_group")
    private String search_group;

    @Column(name = "field_identifier")
    private String fieldIdentifier;

    @Column(name = "value")
    private Object value;
}
