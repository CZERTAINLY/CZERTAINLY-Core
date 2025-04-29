package com.czertainly.core.service.handler;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderCertificateDataDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerAssociation;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.transaction.CertificateValidationEvent;
import com.czertainly.core.messaging.model.ValidationMessage;
import com.czertainly.core.messaging.producers.ValidationProducer;
import com.czertainly.core.service.*;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.X509ObjectToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

@Service
@Transactional
public class CertificateHandler {

    private static final Logger logger = LoggerFactory.getLogger(CertificateHandler.class);

    private AttributeEngine attributeEngine;
    private ValidationProducer validationProducer;

    private TriggerService triggerService;
    private ComplianceService complianceService;
    private CertificateService certificateService;
    private CertificateEventHistoryService certificateEventHistoryService;
    private CryptographicKeyService cryptographicKeyService;

    private CertificateRepository certificateRepository;
    private DiscoveryRepository discoveryRepository;
    private DiscoveryCertificateRepository discoveryCertificateRepository;

    @Autowired
    public void setTriggerService(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setValidationProducer(ValidationProducer validationProducer) {
        this.validationProducer = validationProducer;
    }

    @Autowired
    public void setComplianceService(ComplianceService complianceService) {
        this.complianceService = complianceService;
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
    public void setDiscoveryRepository(DiscoveryRepository discoveryRepository) {
        this.discoveryRepository = discoveryRepository;
    }

    @Autowired
    public void setDiscoveryCertificateRepository(DiscoveryCertificateRepository discoveryCertificateRepository) {
        this.discoveryCertificateRepository = discoveryCertificateRepository;
    }

    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    public void validate(Certificate certificate) {
        if (CertificateUtil.isValidationEnabled(certificate, null)) {
            certificateService.validate(certificate);
        }

        try {
            if (certificate.getRaProfileUuid() != null) {
                complianceService.checkComplianceOfCertificate(certificate);
            }
        } catch (ConnectorException | NotFoundException e) {
            logger.error("Error when checking compliance of certificate {}: {}", certificate.toStringShort(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    public void updateMetadataDefinition(List<MetadataAttribute> metadataAttributes, Map<String, Set<BaseAttributeContent>> metadataContentsMapping, UUID connectorUuid, String connectorName) {
        logger.debug("Updating {} discovery certificate metadata definitions for connector {}", metadataAttributes.size(), connectorName);
        for (MetadataAttribute metadataAttribute : metadataAttributes) {
            try {
                AttributeDefinition attributeDefinition = attributeEngine.updateMetadataAttributeDefinition(metadataAttribute, connectorUuid);
                attributeEngine.registerAttributeContentItems(attributeDefinition.getUuid(), metadataContentsMapping.get(metadataAttribute.getUuid()));
            } catch (AttributeException e) {
                logger.error("Unable to update discovery certificate metadata definition with UUID {} and name {} for discovery connector {}. Message: {}", metadataAttribute.getUuid(), metadataAttribute.getName(), connectorName, e.getMessage(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    public void createDiscoveredCertificate(String batch, DiscoveryHistory discovery, List<DiscoveryProviderCertificateDataDto> discoveredCertificates) {
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

        // report progress
        long currentCount = discoveryCertificateRepository.countByDiscovery(discovery);
        discovery.setMessage(String.format("Downloaded %d %% of discovered certificates from provider (%d / %d)", (int) ((currentCount / (double) discovery.getConnectorTotalCertificatesDiscovered()) * 100), currentCount, discovery.getConnectorTotalCertificatesDiscovered()));
        discoveryRepository.save(discovery);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    public void uploadDiscoveredCertificateKey(PublicKey publicKey, List<UUID> certificateUuids) throws NoSuchAlgorithmException {
        UUID keyUuid = cryptographicKeyService.findKeyByFingerprint(CertificateUtil.getThumbprint(publicKey.getEncoded()));
        Certificate firstCertificate = certificateRepository.findFirstByUuidIn(certificateUuids);
        if (keyUuid == null) {
            keyUuid = cryptographicKeyService.uploadCertificatePublicKey("certKey_" + firstCertificate.getCommonName(), publicKey, firstCertificate.getPublicKeyAlgorithm(), firstCertificate.getKeySize(), firstCertificate.getPublicKeyFingerprint());
        }
        certificateRepository.setKeyUuid(keyUuid, certificateUuids);
    }

    public void updateDiscoveredCertificate(DiscoveryHistory discovery, Certificate certificate, List<MetadataAttribute> metadata) {
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDiscoveredCertificate(EventContext<Certificate> eventContext, int certIndex, int totalCount, DiscoveryHistory discovery, DiscoveryCertificate discoveryCertificate, ConcurrentMap<PublicKey, List<UUID>> keysToCertificatesMap) {
        // Get X509 from discovered certificate and create certificate entity, do not save in database yet
        Certificate certificate;
        X509Certificate x509Cert;
        try {
            x509Cert = CertificateUtil.parseCertificate(discoveryCertificate.getCertificateContent().getContent());
            certificate = certificateService.createCertificateEntity(x509Cert);
        } catch (Exception e) {
            logger.error("Unable to create certificate from discovery certificate with UUID {}: {}", discoveryCertificate.getUuid(), e.getMessage());
            discoveryCertificate.setProcessed(true);
            discoveryCertificate.setProcessedError("Unable to create certificate entity: " + e.getMessage());
            discoveryCertificateRepository.save(discoveryCertificate);
            return;
        }

        try {
            List<TriggerHistory> triggerHistories = new ArrayList<>();
            boolean processed = processIgnoreTriggers(eventContext, certificate, discoveryCertificate.getUuid(), triggerHistories);

            // If some trigger ignored this certificate, certificate is not saved and continue with next one
            if (processed) { // certificate was not ignored
                // Save certificate to database
                certificateService.updateCertificateEntity(certificate);
                // update objectUuid of not ignored certs
                for (TriggerHistory ignoreTriggerHistory : triggerHistories) {
                    ignoreTriggerHistory.setObjectUuid(certificate.getUuid());
                }

                // Evaluate rest of the triggers in given order
                processTriggers(eventContext, certificate, discoveryCertificate.getUuid(), triggerHistories);

                updateDiscoveredCertificate(discovery, certificate, discoveryCertificate.getMeta());
                keysToCertificatesMap.computeIfAbsent(x509Cert.getPublicKey(), k -> new ArrayList<>()).add(certificate.getUuid());
            }
        } catch (RuleException e) {
            logger.error("Unable to process trigger on certificate {} from discovery certificate with UUID {}. Message: {}", certificate.getUuid(), discoveryCertificate.getUuid(), e.getMessage());
        }

        discoveryCertificate.setProcessed(true);
        discoveryCertificateRepository.save(discoveryCertificate);

        // report progress
        if (certIndex % 2 == 0) {
            long currentCount = discoveryCertificateRepository.countByDiscoveryAndNewlyDiscoveredAndProcessed(discovery, true, true);
            discovery.setMessage(String.format("Processed %d %% of newly discovered certificates (%d / %d)", (int) ((currentCount / (double) totalCount) * 100), currentCount, totalCount));
            discoveryRepository.save(discovery);
        }
    }

    private <T extends UniquelyIdentifiedObject> boolean processIgnoreTriggers(EventContext<T> context, T resourceObject, UUID referenceObjectUuid, List<TriggerHistory> triggerHistories) throws RuleException {
        for (TriggerAssociation triggerAssociation : context.getIgnoreTriggers()) {
            Trigger trigger = triggerAssociation.getTrigger();
            TriggerHistory triggerHistory = triggerService.createTriggerHistory(trigger.getUuid(), triggerAssociation.getUuid(), resourceObject.getUuid(), referenceObjectUuid);
            triggerHistories.add(triggerHistory);
            if (context.getRuleEvaluator().evaluateRules(trigger.getRules(), resourceObject, triggerHistory)) {
                triggerHistory.setConditionsMatched(true);
                triggerHistory.setActionsPerformed(true);
                return false;
            } else {
                triggerHistory.setConditionsMatched(false);
                triggerHistory.setActionsPerformed(false);
            }
        }
        return true;
    }

    private <T extends UniquelyIdentifiedObject> void processTriggers(EventContext<T> context, T resourceObject, UUID referenceObjectUuid, List<TriggerHistory> triggerHistories) throws RuleException {
        // Evaluate rest of the triggers in given order
        for (TriggerAssociation triggerAssociation : context.getTriggers()) {
            // Create trigger history entry
            Trigger trigger = triggerAssociation.getTrigger();
            TriggerHistory triggerHistory = triggerService.createTriggerHistory(trigger.getUuid(), triggerAssociation.getUuid(), resourceObject.getUuid(), referenceObjectUuid);
            triggerHistories.add(triggerHistory);
            // If rules are satisfied, perform defined actions
            if (context.getRuleEvaluator().evaluateRules(trigger.getRules(), resourceObject, triggerHistory)) {
                triggerHistory.setConditionsMatched(true);
                context.getRuleEvaluator().performActions(trigger, resourceObject, triggerHistory);
                triggerHistory.setActionsPerformed(triggerHistory.getRecords().isEmpty());
            } else {
                triggerHistory.setConditionsMatched(false);
                triggerHistory.setActionsPerformed(false);
            }
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCertificateValidationEvent(CertificateValidationEvent event) {
        ValidationMessage validationMessage = new ValidationMessage(Resource.CERTIFICATE, event.certificateUuids(), event.discoveryUuid(), event.discoveryName(), event.locationUuid(), event.locationName());
        validationProducer.produceMessage(validationMessage);
    }
}
