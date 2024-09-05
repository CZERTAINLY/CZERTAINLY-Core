
package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.RuleDetailDto;
import com.czertainly.api.model.core.workflows.RuleDto;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "rule")
public class Rule extends UniquelyIdentified {
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_2_condition",
            joinColumns = @JoinColumn(name = "rule_uuid"),
            inverseJoinColumns = @JoinColumn(name = "condition_uuid"))
    @ToString.Exclude
    private List<Condition> conditions;

    @JsonBackReference
    @ManyToMany(mappedBy = "rules", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<Trigger> triggers = new HashSet<>();

    public RuleDto mapToDto() {
        RuleDto ruleDto = new RuleDto();
        ruleDto.setUuid(uuid.toString());
        ruleDto.setName(name);
        ruleDto.setDescription(description);
        ruleDto.setResource(resource);
        return ruleDto;
    }

    public RuleDetailDto mapToDetailDto() {
        RuleDetailDto ruleDetailDto = new RuleDetailDto();
        ruleDetailDto.setUuid(uuid.toString());
        ruleDetailDto.setName(name);
        ruleDetailDto.setDescription(description);
        ruleDetailDto.setResource(resource);
        if (conditions != null) ruleDetailDto.setConditions(conditions.stream().map(Condition::mapToDto).toList());
        
        return ruleDetailDto;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Rule rule = (Rule) o;
        return getUuid() != null && Objects.equals(getUuid(), rule.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
