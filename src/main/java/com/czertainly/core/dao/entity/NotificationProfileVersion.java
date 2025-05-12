package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.notification.NotificationProfileDetailDto;
import com.czertainly.api.model.client.notification.NotificationProfileDto;
import com.czertainly.api.model.client.notification.RecipientDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.notification.RecipientType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "notification_profile_version")
public class NotificationProfileVersion extends UniquelyIdentified {

    @Column(name = "notification_profile_uuid", nullable = false)
    private UUID notificationProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_profile_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private NotificationProfile notificationProfile;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "recipient_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RecipientType recipientType;

    @Column(name = "recipient_uuid")
    private UUID recipientUuid;

    @Column(name = "notification_instance_ref_uuid")
    private UUID notificationInstanceRefUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_instance_ref_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private NotificationInstanceReference notificationInstance;

    @Column(name = "internal_notification", nullable = false)
    private boolean internalNotification;

    @Column(name = "frequency", columnDefinition = "interval")
    private Duration frequency;

    @Column(name = "repetitions")
    private Integer repetitions;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    protected OffsetDateTime createdAt;

    public NotificationProfileDto mapToDto() {
        final NotificationProfileDto notificationProfileDto = new NotificationProfileDto();
        notificationProfileDto.setUuid(this.getNotificationProfile().getUuid().toString());
        notificationProfileDto.setName(this.getNotificationProfile().getName());
        notificationProfileDto.setDescription(this.getNotificationProfile().getDescription());
        notificationProfileDto.setVersion(this.version);
        notificationProfileDto.setRecipientType(this.recipientType);
        notificationProfileDto.setRecipientUuid(this.recipientUuid);
        notificationProfileDto.setNotificationInstanceUuid(this.notificationInstanceRefUuid);
        notificationProfileDto.setInternalNotification(this.internalNotification);

        return notificationProfileDto;
    }

    public NotificationProfileDetailDto mapToDetailDto(RecipientDto recipientDto) {
        final NotificationProfileDetailDto notificationProfileDetailDto = new NotificationProfileDetailDto();
        notificationProfileDetailDto.setUuid(this.getNotificationProfile().getUuid().toString());
        notificationProfileDetailDto.setName(this.getNotificationProfile().getName());
        notificationProfileDetailDto.setDescription(this.getNotificationProfile().getDescription());
        notificationProfileDetailDto.setVersion(this.version);
        notificationProfileDetailDto.setRecipient(recipientDto);
        notificationProfileDetailDto.setNotificationInstance(this.notificationInstance == null ? null : new NameAndUuidDto(notificationInstance.getUuid().toString(), notificationInstance.getName()));
        notificationProfileDetailDto.setInternalNotification(this.internalNotification);
        notificationProfileDetailDto.setFrequency(this.frequency);
        notificationProfileDetailDto.setRepetitions(this.repetitions);

        return notificationProfileDetailDto;
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
