package com.czertainly.core.messaging.listeners;

import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.ValidationMessage;
import com.czertainly.core.service.handler.CertificateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional
public class ValidationListener {
    private static final Logger logger = LoggerFactory.getLogger(ValidationListener.class);

    private CertificateRepository certificateRepository;

    private CertificateHandler certificateHandler;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_VALIDATION_NAME, messageConverter = "jsonMessageConverter", concurrency = "${messaging.concurrency.validation}")
    public void processMessage(final ValidationMessage validationMessage) {
        List<Certificate> certificates;
        if (validationMessage.getUuids() != null) {
            certificates = certificateRepository.findAllByUuidIn(validationMessage.getUuids());

            logger.debug("Validating {} certificate(s)", certificates.size());
            for (Certificate certificate : certificates) {
                certificateHandler.validate(certificate);
            }
            logger.debug("Certificates validated");
        }

        if (validationMessage.getDiscoveryUuid() != null) {
            certificates = certificateRepository.findByValidationStatusAndCertificateContentDiscoveryCertificatesDiscoveryUuid(CertificateValidationStatus.NOT_CHECKED, validationMessage.getDiscoveryUuid());

            logger.debug("Validating {} certificates from discovery {}", certificates.size(), validationMessage.getDiscoveryName());
            for (Certificate certificate : certificates) {
                certificateHandler.validate(certificate);
            }
            logger.debug("Certificates from discovery {} validated", validationMessage.getDiscoveryName());
        }

        if (validationMessage.getLocationUuid() != null) {
            certificates = certificateRepository.findByValidationStatusAndLocationsLocationUuid(CertificateValidationStatus.NOT_CHECKED, validationMessage.getLocationUuid());

            logger.debug("Validating {} certificates from location {}", certificates.size(), validationMessage.getLocationName());
            for (Certificate certificate : certificates) {
                certificateHandler.validate(certificate);
            }
            logger.debug("Certificates from location {} validated", validationMessage.getLocationName());
        }
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setCertificateHandler(CertificateHandler certificateHandler) {
        this.certificateHandler = certificateHandler;
    }
}
