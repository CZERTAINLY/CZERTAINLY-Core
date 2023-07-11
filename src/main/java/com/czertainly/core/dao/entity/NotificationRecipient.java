package com.czertainly.core.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "notification_recipient", uniqueConstraints = {@UniqueConstraint(columnNames = {"notification_uuid", "user_uuid"})})
@NoArgsConstructor
@Setter
@Getter
public class NotificationRecipient extends UniquelyIdentified {
    @Column(name = "notification_uuid", nullable = false)
    private UUID notificationUuid;

    @Column(name = "user_uuid", nullable = false)
    private UUID userUuid;

    @Column(name = "read_at")
    private Date readAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_uuid", insertable = false, updatable = false, nullable = false)
    private Notification notification;
}
