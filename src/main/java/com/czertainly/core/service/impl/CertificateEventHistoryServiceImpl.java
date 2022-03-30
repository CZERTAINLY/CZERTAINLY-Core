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
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CertificateEventHistoryServiceImpl implements CertificateEventHistoryService {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateEventHistoryRepository certificateEventHistoryRepository;

    @Autowired
    private SearchService searchService;

    private static final Logger logger = LoggerFactory.getLogger(CertificateEventHistoryServiceImpl.class);

    @Override
    public void addEventHistory(CertificateEvent event, CertificateEventStatus status, String message, String additionalInformation, Certificate certificate) {
        CertificateEventHistory history = new CertificateEventHistory();
        history.setEvent(event);
        history.setCertificate(certificate);
        history.setStatus(status);
        history.setAdditionalInformation(additionalInformation);
        history.setMessage(message);
        logger.debug("Event history: {}", history);
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
        logger.debug("Event history: {}", history);
        return history;
    }

    @Override
    public List<CertificateEventHistoryDto> getCertificateEventHistory(String uuid) throws NotFoundException {
        Certificate certificate = certificateService.getCertificateEntity(uuid);
        return certificateEventHistoryRepository.findByCertificateOrderByCreatedDesc(certificate).stream().map(CertificateEventHistory::mapToDto).collect(Collectors.toList());
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void asyncSaveAllInBatch(List<CertificateEventHistory> certificateEventHistories){
        certificateEventHistoryRepository.saveAll(certificateEventHistories);
        logger.info("Inserted {} record into the database", certificateEventHistories.size());
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void addEventHistoryForRequest(List<SearchFilterRequestDto> filters, String entity, List<SearchFieldDataDto> originalJson, CertificateEvent event, CertificateEventStatus status, String message){
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        for (Certificate certificate : (List<Certificate>) searchService.completeSearchQueryExecutor(filters, "Certificate", originalJson)) {
            batchHistoryOperationList.add(getEventHistory(event, status, message, "", certificate));
        }
        asyncSaveAllInBatch(batchHistoryOperationList);
    }

}
