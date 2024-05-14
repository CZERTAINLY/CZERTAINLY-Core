package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
@Getter
@Setter
@Entity
@Table(name = "rule_trigger_2_object")
public class RuleTrigger2Object extends UniquelyIdentified {

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "object_uuid", nullable = false)
    private UUID objectUuid;

    @Column(name = "trigger_uuid", nullable = false)
    private UUID triggerUuid;

    @Column(name = "trigger_order")
    private int triggerOrder;
}
