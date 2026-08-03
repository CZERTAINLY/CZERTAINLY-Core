package com.otilm.core.dao.entity.notifications;

import com.otilm.api.model.core.notification.NotificationDataCategory;
import com.otilm.core.dao.entity.UniquelyIdentified;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.*;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "notification_profile")
public class NotificationProfile extends UniquelyIdentified {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Version
    @Column(name = "version_lock", nullable = false)
    private int versionLock;  // Optimistic locking

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    protected OffsetDateTime createdAt;

    // Parent-level, not versioned: a data-exposure switch must apply at send time to every
    // delivery, including monitoring streams pinned to older profile versions.
    @Column(name = "event_data_categories")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<NotificationDataCategory> eventDataCategories = new ArrayList<>();

    /**
     * Never null: rows created before the column existed load as null, which means the same
     * as an empty list -- no enrichment.
     */
    public List<NotificationDataCategory> getEventDataCategories() {
        return eventDataCategories == null ? List.of() : eventDataCategories;
    }

    @ToString.Exclude
    @JsonBackReference
    @OneToMany(mappedBy = "notificationProfile", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("version DESC")
    private List<NotificationProfileVersion> versions = new ArrayList<>();

    @JsonBackReference
    public NotificationProfileVersion getCurrentVersion() {
        return versions.isEmpty() ? null : versions.getFirst();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
