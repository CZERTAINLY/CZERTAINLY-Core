package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEventHistory;
import com.czertainly.core.dao.repository.CertificateEventHistoryRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.SearchService;
import com.czertainly.core.util.MetaDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
// TODO AUTH - should be secured with @ExternalAuthorization?
public class CertificateEventHistoryServiceImpl implements CertificateEventHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(CertificateEventHistoryServiceImpl.class);
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateEventHistoryRepository certificateEventHistoryRepository;
    @Autowired
    private SearchService searchService;

    @Override
    public void addEventHistory(UUID certificateUuid, CertificateEvent event, CertificateEventStatus status, String message, HashMap<String, Object> additionalInformation) {
        addEventHistory(certificateUuid, event, status, message, MetaDefinitions.serialize(additionalInformation));
    }

    @Override
    public void addEventHistory(UUID certificateUuid, CertificateEvent event, CertificateEventStatus status, String message, String additionalInformation) {
        CertificateEventHistory history = new CertificateEventHistory();
        history.setEvent(event);
        history.setCertificateUuid(certificateUuid);
        history.setStatus(status);
        history.setAdditionalInformation(additionalInformation);
        history.setMessage(message);
        certificateEventHistoryRepository.save(history);
    }

    @Override
    public CertificateEventHistory getEventHistory(CertificateEvent event, CertificateEventStatus status, String message, String additionalInformation, Certificate certificate) {
        CertificateEventHistory history = new CertificateEventHistory();
        history.setEvent(event);
        history.setCertificate(certificate);
        history.setStatus(status);
        history.setAdditionalInformation(additionalInformation);
        history.setMessage(message);
        return history;
    }

    @Override
    public List<CertificateEventHistoryDto> getCertificateEventHistory(UUID uuid) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
        return certificateEventHistoryRepository.findByCertificateOrderByCreatedDesc(certificate).stream().map(CertificateEventHistory::mapToDto).collect(Collectors.toList());
    }

    @Override
    @Async
    public void asyncSaveAllInBatch(List<CertificateEventHistory> certificateEventHistories) {
        certificateEventHistoryRepository.saveAll(certificateEventHistories);
        logger.info("Inserted {} record into the database", certificateEventHistories.size());
    }

    @Override
    @Async
    public void addEventHistoryForRequest(List<SearchFilterRequestDto> filters, String entity, List<SearchFieldDataDto> originalJson, CertificateEvent event, CertificateEventStatus status, String message) {
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        for (Certificate certificate : (List<Certificate>) searchService.completeSearchQueryExecutor(filters, "Certificate", originalJson)) {
            batchHistoryOperationList.add(getEventHistory(event, status, message, "", certificate));
        }
        asyncSaveAllInBatch(batchHistoryOperationList);
    }

}
