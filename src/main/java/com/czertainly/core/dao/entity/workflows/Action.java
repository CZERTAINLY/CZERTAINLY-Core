
package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.ActionDetailDto;
import com.czertainly.api.model.core.workflows.ActionDto;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "action")
public class Action extends UniquelyIdentified {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "action_2_execution",
            joinColumns = @JoinColumn(name = "action_uuid"),
            inverseJoinColumns = @JoinColumn(name = "execution_uuid"))
    private List<Execution> executions;

    public ActionDto mapToDto() {
        ActionDto actionDto = new ActionDto();
        actionDto.setUuid(uuid.toString());
        actionDto.setName(name);
        actionDto.setDescription(description);
        actionDto.setResource(resource);
        return actionDto;
    }

    public ActionDetailDto mapToDetailDto() {
        ActionDetailDto actionDetailDto = new ActionDetailDto();
        actionDetailDto.setUuid(uuid.toString());
        actionDetailDto.setName(name);
        actionDetailDto.setDescription(description);
        actionDetailDto.setResource(resource);
        if (executions != null) actionDetailDto.setExecutions(executions.stream().map(Execution::mapToDto).toList());
        return actionDetailDto;
    }

}
