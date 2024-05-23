
package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.workflows.TriggerDetailDto;
import com.czertainly.api.model.core.workflows.TriggerDto;
import com.czertainly.api.model.core.workflows.TriggerType;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "trigger")
public class Trigger extends UniquelyIdentified {
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TriggerType type;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "ignore_object", nullable = false)
    private boolean ignoreObject;

    @Column(name = "event")
    @Enumerated(EnumType.STRING)
    private ResourceEvent event;

    @Column(name = "event_resource")
    @Enumerated(EnumType.STRING)
    private Resource eventResource;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "trigger_2_rule",
            joinColumns = @JoinColumn(name = "trigger_uuid"),
            inverseJoinColumns = @JoinColumn(name = "rule_uuid"))
    private List<Rule> rules;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "trigger_2_action",
            joinColumns = @JoinColumn(name = "trigger_uuid"),
            inverseJoinColumns = @JoinColumn(name = "action_uuid"))
    private List<Action> actions;

    public TriggerDto mapToDto() {
        TriggerDto triggerDto = new TriggerDto();
        triggerDto.setUuid(uuid.toString());
        triggerDto.setName(name);
        triggerDto.setDescription(description);
        triggerDto.setType(type);
        triggerDto.setResource(resource);
        triggerDto.setIgnoreObject(ignoreObject);
        triggerDto.setEvent(event);
        triggerDto.setEventResource(eventResource);
        return triggerDto;
    }

    public TriggerDetailDto mapToDetailDto() {
        TriggerDetailDto triggerDetailDto = new TriggerDetailDto();
        triggerDetailDto.setUuid(uuid.toString());
        triggerDetailDto.setName(name);
        triggerDetailDto.setDescription(description);
        triggerDetailDto.setType(type);
        triggerDetailDto.setResource(resource);
        triggerDetailDto.setIgnoreObject(ignoreObject);
        triggerDetailDto.setEvent(event);
        triggerDetailDto.setEventResource(eventResource);

        if (rules != null) triggerDetailDto.setRules(rules.stream().map(Rule::mapToDto).toList());
        if (actions != null) triggerDetailDto.setActions(actions.stream().map(Action::mapToDto).toList());
        return triggerDetailDto;
    }
}
