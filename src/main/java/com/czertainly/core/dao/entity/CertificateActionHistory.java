package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.certificate.CertificateAction;
import com.czertainly.api.model.core.certificate.CertificateActionStatus;
import com.czertainly.api.model.core.certificate.CertificateHistory;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "certificate_action_history")
public class CertificateActionHistory extends Audited implements Serializable, DtoMapper<CertificateHistory> {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "certificate_action_history_seq")
    @SequenceGenerator(name = "certificate_action_history_seq", sequenceName = "certificate_action_history_id_seq", allocationSize = 1)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private CertificateAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CertificateActionStatus status;

    @Column(name="message")
    private String message;

    @Column(name="additional_information")
    private String additionalInformation;

    @ManyToOne
    @JoinColumn(name = "certificate_id", nullable = false)
    private Certificate certificate;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("uuid", uuid)
                .append("created", created)
                .append("createdBy", author)
                .append("status", status)
                .append("action", action)
                .append("message", message)
                .toString();
    }

    @Override
    public CertificateHistory mapToDto(){
        CertificateHistory certificateHistory = new CertificateHistory();
        certificateHistory.setCertificateUuid(certificate.getUuid());
        certificateHistory.setAction(action);
        try {
            certificateHistory.setAdditionalInformation(new ObjectMapper().readValue(additionalInformation, Object.class));
        } catch (JsonProcessingException e) {
            certificateHistory.setAdditionalInformation(additionalInformation);
        }
        certificateHistory.setMessage(message);
        certificateHistory.setUuid(uuid);
        certificateHistory.setCreated(created);
        certificateHistory.setCreatedBy(author);
        certificateHistory.setStatus(status);
        return certificateHistory;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CertificateAction getAction() {
        return action;
    }

    public void setAction(CertificateAction action) {
        this.action = action;
    }

    public CertificateActionStatus getStatus() {
        return status;
    }

    public void setStatus(CertificateActionStatus status) {
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
    }
}
