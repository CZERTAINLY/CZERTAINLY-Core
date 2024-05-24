package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "certificate_event_history")
public class CertificateEventHistory extends UniquelyIdentified implements Serializable, DtoMapper<CertificateEventHistoryDto> {

    @Enumerated(EnumType.STRING)
    @Column(name = "event")
    private CertificateEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CertificateEventStatus status;

    @Column(name="message")
    private String message;

    @Column(name="additional_information", columnDefinition = "TEXT")
    private String additionalInformation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_uuid", nullable = false, insertable = false, updatable = false)
    private Certificate certificate;

    @Column(name = "certificate_uuid", nullable = false)
    private UUID certificateUuid;

    @Column(name = "i_author")
    @CreatedBy
    @LastModifiedBy
    private String author;

    @Column(name = "i_cre", nullable = false, updatable = false)
    @CreatedDate
    private Date created;

    @Column(name = "i_upd", nullable = false)
    @LastModifiedDate
    private Date updated;

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
    public CertificateEventHistoryDto mapToDto(){
        CertificateEventHistoryDto certificateEventHistoryDto = new CertificateEventHistoryDto();
        certificateEventHistoryDto.setCertificateUuid(certificate.getUuid().toString());
        certificateEventHistoryDto.setEvent(event);
        try {
            certificateEventHistoryDto.setAdditionalInformation(new ObjectMapper().readValue(additionalInformation, HashMap.class));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            certificateEventHistoryDto.setAdditionalInformation(null);
        }
        certificateEventHistoryDto.setMessage(message);
        certificateEventHistoryDto.setUuid(uuid.toString());
        certificateEventHistoryDto.setCreated(created);
        certificateEventHistoryDto.setCreatedBy(author);
        certificateEventHistoryDto.setStatus(status);
        return certificateEventHistoryDto;
    }

    public CertificateEvent getEvent() {
        return event;
    }

    public void setEvent(CertificateEvent event) {
        this.event = event;
    }

    public CertificateEventStatus getStatus() {
        return status;
    }

    public void setStatus(CertificateEventStatus status) {
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

    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
        this.certificateUuid = certificate.getUuid();
    }

    public UUID getCertificateUuid() {
        return certificateUuid;
    }

    public void setCertificateUuid(UUID certificateUuid) {
        this.certificateUuid = certificateUuid;
    }

    public void setCertificateUuid(String certificateUuid) {
        this.certificateUuid = UUID.fromString(certificateUuid);
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }
}
