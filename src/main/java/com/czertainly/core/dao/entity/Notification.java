package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.notification.NotificationDto;
import com.czertainly.api.model.core.auth.Resource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "notification")
@NoArgsConstructor
@Setter
@Getter
public class Notification extends UniquelyIdentified {
    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "detail")
    private String detail;

    @Column(name = "sent_at", nullable = false, columnDefinition = "TIMESTAMP default CURRENT_TIMESTAMP")
    private Date sentAt = new Date();

    @Column(name = "target_object_type")
    @Enumerated(EnumType.STRING)
    private Resource targetObjectType;

    @Column(name = "target_object_identification")
    private String targetObjectIdentification;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<NotificationRecipient> notificationRecipients;

    public NotificationDto mapToDto() {
        Optional<NotificationRecipient> notificationRecipient = this.notificationRecipients.stream().findFirst();

        NotificationDto dto = new NotificationDto();
        dto.setUuid(this.getUuid());
        dto.setMessage(this.message);
        dto.setDetail(this.detail);
        dto.setSentAt(this.sentAt);
        dto.setTargetObjectType(this.targetObjectType);
        if (this.targetObjectIdentification != null) {
            dto.setTargetObjectIdentification(List.of(this.targetObjectIdentification.split(",")));
        }
        notificationRecipient.ifPresent(recipient -> dto.setReadAt(recipient.getReadAt()));
        return dto;
    }
}
