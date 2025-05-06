package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "trigger_association")
public class TriggerAssociation extends UniquelyIdentified {

    @Column(name = "trigger_uuid", nullable = false)
    private UUID triggerUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_uuid", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private Trigger trigger;

    @Column(name = "resource")
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "object_uuid")
    private UUID objectUuid;

    @Column(name = "trigger_order")
    private int triggerOrder;

    @Column(name = "event")
    @Enumerated(EnumType.STRING)
    private ResourceEvent event;

    @Column(name = "override")
    private boolean override;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        TriggerAssociation that = (TriggerAssociation) o;
        return getUuid() != null && Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
