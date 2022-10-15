package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

@Entity
@Table(name = "certificate_event_history")
public class CertificateEventHistory extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<CertificateEventHistoryDto> {

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

    @ManyToOne
    @JoinColumn(name = "certificate_uuid", nullable = false, insertable = false, updatable = false)
    private Certificate certificate;

    @Column(name = "certificate_uuid", nullable = false)
    private UUID certificateUuid;

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
}
