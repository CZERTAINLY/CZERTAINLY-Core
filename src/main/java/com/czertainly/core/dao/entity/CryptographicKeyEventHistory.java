package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.cryptography.key.KeyEvent;
import com.czertainly.api.model.core.cryptography.key.KeyEventHistoryDto;
import com.czertainly.api.model.core.cryptography.key.KeyEventStatus;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

@Entity
@Table(name = "key_event_history")
public class CryptographicKeyEventHistory extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<KeyEventHistoryDto> {

    @Enumerated(EnumType.STRING)
    @Column(name = "event")
    private KeyEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private KeyEventStatus status;

    @Column(name = "message")
    private String message;

    @Column(name = "additional_information", columnDefinition = "TEXT")
    private String additionalInformation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_uuid", nullable = false, insertable = false, updatable = false)
    private CryptographicKeyItem key;

    @Column(name = "key_uuid", nullable = false)
    private UUID keyUuid;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("created", created)
                .append("createdBy", author)
                .append("status", status)
                .append("event", event)
                .append("message", message)
                .toString();
    }

    @Override
    public KeyEventHistoryDto mapToDto() {
        KeyEventHistoryDto keyEventHistoryDto = new KeyEventHistoryDto();
        keyEventHistoryDto.setKeyUuid(key.getUuid().toString());
        keyEventHistoryDto.setEvent(event);
        try {
            keyEventHistoryDto.setAdditionalInformation(new ObjectMapper().readValue(additionalInformation, HashMap.class));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            keyEventHistoryDto.setAdditionalInformation(null);
        }
        keyEventHistoryDto.setMessage(message);
        keyEventHistoryDto.setUuid(uuid.toString());
        keyEventHistoryDto.setCreated(created);
        keyEventHistoryDto.setCreatedBy(author);
        keyEventHistoryDto.setStatus(status);
        return keyEventHistoryDto;
    }

    public KeyEvent getEvent() {
        return event;
    }

    public void setEvent(KeyEvent event) {
        this.event = event;
    }

    public KeyEventStatus getStatus() {
        return status;
    }

    public void setStatus(KeyEventStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public CryptographicKeyItem getKey() {
        return key;
    }

    public void setKey(CryptographicKeyItem key) {
        this.key = key;
        if (key != null) this.setKeyUuid(key.getUuid());
    }

    public UUID getKeyUuid() {
        return keyUuid;
    }

    public void setKeyUuid(UUID keyUuid) {
        this.keyUuid = keyUuid;
    }
}
