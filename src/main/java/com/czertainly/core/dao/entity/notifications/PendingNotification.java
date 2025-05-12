package com.czertainly.core.dao.entity.notifications;

import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.ResourceObjectAssociation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "pending_notification")
public class PendingNotification  extends ResourceObjectAssociation {

    @Column(name = "notification_profile_uuid", nullable = false)
    private UUID notificationProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_profile_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private NotificationProfile notificationProfile;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "event")
    @Enumerated(EnumType.STRING)
    private ResourceEvent event;

    @Column(name = "last_sent_at", nullable = false, updatable = false)
    @UpdateTimestamp
    protected OffsetDateTime lastSentAt;

    @Column(name = "repetitions")
    private Integer repetitions;

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
