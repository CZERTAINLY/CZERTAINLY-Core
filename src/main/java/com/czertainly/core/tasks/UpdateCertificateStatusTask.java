package com.czertainly.core.tasks;

import com.czertainly.core.service.CertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

public class UpdateCertificateStatusTask {

    @Autowired
    private CertificateService certificateService;

    // scheduled for every hour, to process 1/24 of eligible certificates for status update
    @Scheduled(fixedRate = 1000*60*60, initialDelay = 10000)
    public void performTask() {
        certificateService.updateCertificatesStatusScheduled();
    }
}
