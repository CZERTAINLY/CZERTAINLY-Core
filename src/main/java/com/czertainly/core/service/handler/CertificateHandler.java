package com.czertainly.core.service.handler;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderCertificateDataDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.DiscoveryCertificate;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.evaluator.CertificateRuleEvaluator;
import com.czertainly.core.event.transaction.CertificateValidationEvent;
import com.czertainly.core.event.transaction.DiscoveryProgressEvent;
import com.czertainly.core.messaging.model.ValidationMessage;
import com.czertainly.core.messaging.producers.ValidationProducer;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.TriggerService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.X509ObjectToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
@Transactional
public class CertificateHandler {

    private static final Logger logger = LoggerFactory.getLogger(CertificateHandler.class);

    private static final int MAX_PARALLEL_BATCHES = 20;
    private static final Semaphore createCertSemaphore = new Semaphore(MAX_PARALLEL_BATCHES);
    private static final Semaphore processCertSemaphore = new Semaphore(MAX_PARALLEL_BATCHES);

    private AttributeEngine attributeEngine;
    private CertificateRuleEvaluator certificateRuleEvaluator;
    private ApplicationEventPublisher applicationEventPublisher;
    private ValidationProducer validationProducer;

    private TriggerService triggerService;
    private CertificateService certificateService;
    private CertificateEventHistoryService certificateEventHistoryService;

    private CertificateRepository certificateRepository;
    private DiscoveryCertificateRepository discoveryCertificateRepository;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setCertificateRuleEvaluator(CertificateRuleEvaluator certificateRuleEvaluator) {
        this.certificateRuleEvaluator = certificateRuleEvaluator;
    }

    @Autowired
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired
    public void setValidationProducer(ValidationProducer validationProducer) {
        this.validationProducer = validationProducer;
    }

    @Autowired
    public void setTriggerService(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setCertificateEventHistoryService(CertificateEventHistoryService certificateEventHistoryService) {
        this.certificateEventHistoryService = certificateEventHistoryService;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setDiscoveryCertificateRepository(DiscoveryCertificateRepository discoveryCertificateRepository) {
        this.discoveryCertificateRepository = discoveryCertificateRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    public void validate(Certificate certificate) {
        certificateService.validate(certificate);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    public void createDiscoveredCertificate(String batch, DiscoveryHistory discovery, List<DiscoveryProviderCertificateDataDto> discoveredCertificates) {
        try {
            logger.trace("Waiting to download batch {} of discovered certificates for discovery {}.", batch, discovery.getName());
            createCertSemaphore.acquire();
            logger.trace("Downloading batch {} of discovered certificates for discovery {}.", batch, discovery.getName());

            for (DiscoveryProviderCertificateDataDto certificate : discoveredCertificates) {
                DiscoveryCertificate discoveryCertificate = null;
                try {
                    X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate.getBase64Content());
                    String fingerprint = CertificateUtil.getThumbprint(x509Cert.getEncoded());
                    Certificate existingCertificate = certificateRepository.findByFingerprint(fingerprint).orElse(null);

                    discoveryCertificate = CertificateUtil.prepareDiscoveryCertificate(existingCertificate, x509Cert);
                    discoveryCertificate.setDiscovery(discovery);
                    discoveryCertificate.setNewlyDiscovered(existingCertificate == null);
                    discoveryCertificate.setMeta(certificate.getMeta());

                    if (existingCertificate == null) {
                        discoveryCertificate.setCertificateContent(certificateService.checkAddCertificateContent(fingerprint, X509ObjectToString.toPem(x509Cert)));
                    } else {
                        updateDiscoveredCertificate(discovery, existingCertificate, certificate.getMeta());
                        discoveryCertificate.setProcessed(true);
                    }

                    discoveryCertificateRepository.save(discoveryCertificate);
                } catch (Exception e) {
                    logger.error("Unable to create discovery certificate {} in batch {} for discovery {}. Message: {}", discoveryCertificate == null ? certificate.getUuid() : discoveryCertificate.getCommonName(), batch, discovery.getName(), e.getMessage(), e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Downloading batch {} of discovered certificates for discovery {} interrupted.", batch, discovery.getName());
        } finally {
            logger.trace("Downloading batch {} of discovered certificates for discovery {} finalized. Released semaphore.", batch, discovery.getName());
            createCertSemaphore.release();
        }

        applicationEventPublisher.publishEvent(new DiscoveryProgressEvent(discovery.getUuid(), discovery.getTotalCertificatesDiscovered(), true));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    public void processDiscoveredCertificate(int certIndex, int totalCount, DiscoveryHistory discovery, DiscoveryCertificate discoveryCertificate, List<Trigger> ignoreTriggers, List<Trigger> orderedTriggers) {
        try {
            logger.trace("Waiting to process cert {} of discovered certificates for discovery {}.", certIndex, discovery.getName());
            processCertSemaphore.acquire();
            logger.trace("Processing cert {} of discovered certificates for discovery {}.", certIndex, discovery.getName());

            // Get X509 from discovered certificate and create certificate entity, do not save in database yet
            Certificate entry;
            X509Certificate x509Cert;
            try {
                x509Cert = CertificateUtil.parseCertificate(discoveryCertificate.getCertificateContent().getContent());
                entry = certificateService.createCertificateEntity(x509Cert);
            } catch (Exception e) {
                logger.error("Unable to create certificate from discovery certificate with UUID {}: {}", discoveryCertificate.getUuid(), e.getMessage());
                discoveryCertificate.setProcessed(true);
                discoveryCertificate.setProcessedError("Unable to create certificate entity: " + e.getMessage());
                discoveryCertificateRepository.save(discoveryCertificate);
                return;
            }

            try {
                // First, check the triggers that have action with action type set to ignore
                boolean ignored = false;
                List<TriggerHistory> ignoreTriggerHistories = new ArrayList<>();
                for (Trigger trigger : ignoreTriggers) {
                    TriggerHistory triggerHistory = triggerService.createTriggerHistory(OffsetDateTime.now(), trigger.getUuid(), discovery.getUuid(), null, discoveryCertificate.getUuid());
                    if (certificateRuleEvaluator.evaluateRules(trigger.getRules(), entry, triggerHistory)) {
                        ignored = true;
                        triggerHistory.setConditionsMatched(true);
                        triggerHistory.setActionsPerformed(true);
                        break;
                    } else {
                        triggerHistory.setConditionsMatched(false);
                        triggerHistory.setActionsPerformed(false);
                    }
                    ignoreTriggerHistories.add(triggerHistory);
                }

                // If some trigger ignored this certificate, certificate is not saved and continue with next one
                if (ignored) {
                    return;
                }

                // Save certificate to database
                certificateService.updateCertificateEntity(entry);

                // update objectUuid of not ignored certs
                for (TriggerHistory ignoreTriggerHistory : ignoreTriggerHistories) {
                    ignoreTriggerHistory.setObjectUuid(entry.getUuid());
                }

                // Evaluate rest of the triggers in given order
                for (Trigger trigger : orderedTriggers) {
                    // Create trigger history entry
                    TriggerHistory triggerHistory = triggerService.createTriggerHistory(OffsetDateTime.now(), trigger.getUuid(), discovery.getUuid(), entry.getUuid(), discoveryCertificate.getUuid());
                    // If rules are satisfied, perform defined actions
                    if (certificateRuleEvaluator.evaluateRules(trigger.getRules(), entry, triggerHistory)) {
                        triggerHistory.setConditionsMatched(true);
                        certificateRuleEvaluator.performActions(trigger, entry, triggerHistory);
                        triggerHistory.setActionsPerformed(triggerHistory.getRecords().isEmpty());
                    } else {
                        triggerHistory.setConditionsMatched(false);
                        triggerHistory.setActionsPerformed(false);
                    }
                }
            } catch (RuleException e) {
                logger.error("Unable to process trigger on certificate {} from discovery certificate with UUID {}. Message: {}", entry.getUuid(), discoveryCertificate.getUuid(), e.getMessage());
                return;
            }

            updateDiscoveredCertificate(discovery, entry, discoveryCertificate.getMeta());
            discoveryCertificate.setProcessed(true);
            discoveryCertificateRepository.save(discoveryCertificate);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread {} processing cert {} of discovered certificates interrupted.", Thread.currentThread().getName(), certIndex);
        } finally {
            logger.trace("Thread {} processing cert {} of discovered certificates finalized. Released semaphore.", Thread.currentThread().getName(), certIndex);
            processCertSemaphore.release();
        }

        if (certIndex % 100 == 0) {
            applicationEventPublisher.publishEvent(new DiscoveryProgressEvent(discovery.getUuid(), totalCount, false));
        }
    }

    private void updateDiscoveredCertificate(DiscoveryHistory discovery, Certificate certificate, List<MetadataAttribute> metadata) {
        // Set metadata attributes, create certificate event history entry and validate certificate
        try {
            attributeEngine.updateMetadataAttributes(metadata, new ObjectAttributeContentInfo(discovery.getConnectorUuid(), Resource.CERTIFICATE, certificate.getUuid(), Resource.DISCOVERY, discovery.getUuid(), discovery.getName()));
        } catch (AttributeException e) {
            logger.error("Could not update metadata for discovery certificate {}.", certificate.getUuid());
        }
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("Discovery Name", discovery.getName());
        additionalInfo.put("Discovery UUID", discovery.getUuid());
        additionalInfo.put("Discovery Connector Name", discovery.getConnectorName());
        additionalInfo.put("Discovery Kind", discovery.getKind());
        certificateEventHistoryService.addEventHistory(
                certificate.getUuid(),
                CertificateEvent.DISCOVERY,
                CertificateEventStatus.SUCCESS,
                "Discovered from Connector: " + discovery.getConnectorName() + " via discovery: " + discovery.getName(),
                MetaDefinitions.serialize(additionalInfo)
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCertificateValidationEvent(CertificateValidationEvent event) {
        ValidationMessage validationMessage = new ValidationMessage(Resource.CERTIFICATE, event.certificateUuids(), event.discoveryUuid(), event.discoveryName(), event.locationUuid(), event.locationName());
        validationProducer.produceMessage(validationMessage);
    }
}
