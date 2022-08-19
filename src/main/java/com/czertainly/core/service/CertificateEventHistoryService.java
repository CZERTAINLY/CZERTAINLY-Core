package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEventHistory;

import java.util.HashMap;
import java.util.List;

// TODO AUTH - should be secured?
public interface CertificateEventHistoryService {
    List<CertificateEventHistoryDto> getCertificateEventHistory(String uuid) throws NotFoundException;
    void addEventHistory(CertificateEvent event, CertificateEventStatus status, String message, String additionalInformation, Certificate certificate);
    CertificateEventHistory getEventHistory(CertificateEvent event, CertificateEventStatus status, String message, String additionalInformation, Certificate certificate);
    void asyncSaveAllInBatch(List<CertificateEventHistory> certificateEventHistories);
    void addEventHistoryForRequest(List<SearchFilterRequestDto> filters, String entity, List<SearchFieldDataDto> originalJson, CertificateEvent event, CertificateEventStatus status, String message);

    /**
     * Method to add event into the Certificate history.
     * @param event Certificate event
     * @param status Event result
     * @param message Short message for the event
     * @param additionalInformation Additional information as key-value pairs
     * @param certificate Certificate entity that should record the event
     */
    void addEventHistory(CertificateEvent event, CertificateEventStatus status, String message, HashMap<String, Object> additionalInformation, Certificate certificate);
}
