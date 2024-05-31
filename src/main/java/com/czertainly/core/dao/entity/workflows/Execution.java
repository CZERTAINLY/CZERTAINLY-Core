
package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.ExecutionDto;
import com.czertainly.api.model.core.workflows.ExecutionType;
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
@Table(name = "execution")
public class Execution extends UniquelyIdentified {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ExecutionType type;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @OneToMany(mappedBy = "execution", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ExecutionItem> items;

    @JsonBackReference
    @ManyToMany(mappedBy = "executions", fetch = FetchType.LAZY)
    private Set<Action> actions = new HashSet<>();

    public ExecutionDto mapToDto() {
        ExecutionDto executionDto = new ExecutionDto();
        executionDto.setUuid(uuid.toString());
        executionDto.setName(name);
        executionDto.setDescription(description);
        executionDto.setType(type);
        executionDto.setResource(resource);
        if (items != null) executionDto.setItems(items.stream().map(ExecutionItem::mapToDto).toList());
        return executionDto;
    }
}
