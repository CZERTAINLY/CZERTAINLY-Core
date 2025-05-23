package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public interface CertificateEventHistoryService {

    List<CertificateEventHistoryDto> getCertificateEventHistory(UUID uuid) throws NotFoundException;

    /**
     * Method to add event into the Certificate history.
     * @param certificateUuid UUID of certificate that should record the event
     * @param event Certificate event
     * @param status Event result
     * @param message Short message for the event
     * @param additionalInformation Additional information as key-value pairs
     */
    void addEventHistory(UUID certificateUuid, CertificateEvent event, CertificateEventStatus status, String message, HashMap<String, Object> additionalInformation);

    void addEventHistory(UUID certificateUuid, CertificateEvent event, CertificateEventStatus status, String message, String additionalInformation);

}
