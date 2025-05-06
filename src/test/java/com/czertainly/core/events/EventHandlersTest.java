package com.czertainly.core.events;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.events.handlers.CertificateStatusChangedEventHandler;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

class EventHandlersTest extends BaseSpringBootTest {

    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateEventHistoryService certificateEventHistoryService;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private CertificateStatusChangedEventHandler certificateStatusChangedEventHandler;

    @Test
    @Disabled
    void testEvents() throws EventException, NotFoundException {
        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        Certificate certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCercertificatetificate");
        certificate.setSerialNumber("123456789");
        certificate.setCertificateType(CertificateType.X509);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.INACTIVE);
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate = certificateRepository.save(certificate);

        certificateService.validate(certificate);

        certificateStatusChangedEventHandler.handleEvent(CertificateStatusChangedEventHandler.constructEventMessage(certificate.getUuid(), CertificateValidationStatus.INACTIVE, certificate.getValidationStatus()));

        List<CertificateEventHistoryDto> historyList = certificateEventHistoryService.getCertificateEventHistory(certificate.getUuid());
        Assertions.assertEquals(1, historyList.size());
        Assertions.assertEquals(CertificateEvent.UPDATE_VALIDATION_STATUS, historyList.getFirst().getEvent());
    }

}
