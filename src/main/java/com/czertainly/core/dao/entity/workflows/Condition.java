
package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.ConditionType;
import com.czertainly.api.model.core.workflows.ConditionDto;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "condition")
public class Condition extends UniquelyIdentified {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConditionType type;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @OneToMany(mappedBy = "condition", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ConditionItem> items;

    @JsonBackReference
    @ManyToMany(mappedBy = "conditions", fetch = FetchType.LAZY)
    private Set<Rule> rules = new HashSet<>();

    public ConditionDto mapToDto() {
        ConditionDto conditionDto = new ConditionDto();
        conditionDto.setUuid(uuid.toString());
        conditionDto.setName(name);
        conditionDto.setDescription(description);
        conditionDto.setType(type);
        conditionDto.setResource(resource);
        if (items != null) conditionDto.setItems(items.stream().map(ConditionItem::mapToDto).toList());

        return conditionDto;
    }

}
