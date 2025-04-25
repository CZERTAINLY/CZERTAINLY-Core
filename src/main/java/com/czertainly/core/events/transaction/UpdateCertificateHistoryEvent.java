package com.czertainly.core.events.transaction;

import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;

import java.util.UUID;

public record UpdateCertificateHistoryEvent(UUID certificateUuid, CertificateEvent certificateEvent, CertificateEventStatus eventStatus, String message, String detail) {

    public UpdateCertificateHistoryEvent(UUID certificateUuid, CertificateEvent certificateEvent, CertificateEventStatus eventStatus, IPlatformEnum oldStatus, IPlatformEnum newStatus) {
        this(certificateUuid, certificateEvent, eventStatus, "Certificate %s changed from %s to %s.".formatted(certificateEvent == CertificateEvent.UPDATE_STATE ? "state" : "validation status", oldStatus.getLabel(), newStatus.getLabel()), null);
    }
}
