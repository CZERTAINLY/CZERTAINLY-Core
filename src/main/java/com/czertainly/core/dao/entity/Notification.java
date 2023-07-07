package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.notification.NotificationDto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<NotificationRecipient> notificationRecipients;

    public NotificationDto mapToDto() {
        Optional<NotificationRecipient> notificationRecipient = this.notificationRecipients.stream().findFirst();

        NotificationDto dto = new NotificationDto();
        dto.setUuid(this.getUuid());
        dto.setMessage(this.message);
        dto.setDetail(this.detail);
        notificationRecipient.ifPresent(recipient -> dto.setReadAt(recipient.getReadAt()));
        return dto;
    }
}
