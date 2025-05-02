package com.czertainly.core.events.handlers;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.DiscoveryCertificate;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.evaluator.CertificateRuleEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.events.data.DiscoveryResult;
import com.czertainly.core.events.transaction.TransactionHandler;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.model.ValidationMessage;
import com.czertainly.core.messaging.producers.ValidationProducer;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.handler.CertificateHandler;
import com.czertainly.core.tasks.ScheduledJobInfo;
import com.czertainly.core.util.CertificateUtil;
import com.pivovarit.collectors.ParallelCollectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Transactional
@Component(ResourceEvent.Codes.CERTIFICATE_DISCOVERED)
public class CertificateDiscoveredEventHandler extends EventHandler<Certificate> {

    private static final Integer MAXIMUM_PARALLELISM = 5;
    private static final Logger logger = LoggerFactory.getLogger(CertificateDiscoveredEventHandler.class);

    private static final Semaphore processCertSemaphore = new Semaphore(10);

    private CertificateHandler certificateHandler;
    private TransactionHandler transactionHandler;
    private CertificateRuleEvaluator ruleEvaluator;
    private ValidationProducer validationProducer;

    private CertificateService certificateService;
    private DiscoveryRepository discoveryRepository;
    private DiscoveryCertificateRepository discoveryCertificateRepository;

    @Autowired
    public void setDiscoveryCertificateHandler(CertificateHandler certificateHandler) {
        this.certificateHandler = certificateHandler;
    }

    @Autowired
    public void setTransactionHandler(TransactionHandler transactionHandler) {
        this.transactionHandler = transactionHandler;
    }

    @Autowired
    public void setRuleEvaluator(CertificateRuleEvaluator ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    @Autowired
    public void setValidationProducer(ValidationProducer validationProducer) {
        this.validationProducer = validationProducer;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setDiscoveryRepository(DiscoveryRepository discoveryRepository) {
        this.discoveryRepository = discoveryRepository;
    }

    @Autowired
    public void setDiscoveryCertificateRepository(DiscoveryCertificateRepository discoveryCertificateRepository) {
        this.discoveryCertificateRepository = discoveryCertificateRepository;
    }

    @Override
    protected EventContext<Certificate> prepareContext(EventMessage eventMessage) {
        EventContext<Certificate> context = new EventContext<>(eventMessage, ruleEvaluator, null);
        // TODO: load event triggers from platform settings
        loadTriggers(context, eventMessage.getOverrideResource(), eventMessage.getOverrideObjectUuid());

        return context;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handleEvent(EventMessage eventMessage) throws EventException {
        if (eventMessage.getOverrideResource() == null || eventMessage.getOverrideObjectUuid() == null) {
            throw new EventException(eventMessage.getResourceEvent(), "Event currently supported only through discovery as overriding resource");
        }

        EventContext<Certificate> context = prepareContext(eventMessage);

        // Get newly discovered certificates
        DiscoveryHistory discovery = discoveryRepository.findByUuid(eventMessage.getOverrideObjectUuid()).orElseThrow(() -> new EventException(eventMessage.getResourceEvent(), "Discovery with UUID %s not found".formatted(eventMessage.getOverrideObjectUuid())));
        String originalMessage = discovery.getStatus() != DiscoveryStatus.IN_PROGRESS ? discovery.getMessage() : null;
        List<DiscoveryCertificate> discoveredCertificates = discoveryCertificateRepository.findByDiscoveryUuidAndNewlyDiscovered(eventMessage.getOverrideObjectUuid(), true, Pageable.unpaged());
        logger.debug("Going to process {} triggers on {} discovered certificates", context.getIgnoreTriggers().size() + context.getTriggers().size(), context.getResourceObjects().size());

        if (discoveredCertificates.isEmpty()) {
            eventProducer.produceMessage(DiscoveryFinishedEventHandler.constructEventMessage(discovery.getUuid(), context.getUserUuid(), context.getScheduledJobInfo(), new DiscoveryResult(DiscoveryStatus.PROCESSING, originalMessage)));
            return;
        }

        // For each discovered certificate and for each found trigger, check if it satisfies rules defined by the trigger and perform actions accordingly
        AtomicInteger index = new AtomicInteger(0);
        ConcurrentMap<PublicKey, List<UUID>> keyToCertificates = new ConcurrentHashMap<>();
        try (ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            DelegatingSecurityContextExecutor executor = new DelegatingSecurityContextExecutor(virtualThreadExecutor, securityContext);
            CompletableFuture<Stream<Object>> future = discoveredCertificates.stream().collect(
                    ParallelCollectors.parallel(
                            discoveryCertificate -> {
                                int certIndex;
                                try {
                                    certIndex = index.incrementAndGet();
                                    processCertSemaphore.acquire();
                                    transactionHandler.runInNewTransaction(() -> processDiscoveredCertificate(context, certIndex, discoveredCertificates.size(), discovery, discoveryCertificate, keyToCertificates));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    logger.error("Thread {} processing cert {} of discovered certificates interrupted.", Thread.currentThread().getName(), index.get());
                                } catch (Exception e) {
                                    logger.error("Unable to process certificate {}: {}", discoveryCertificate.getCommonName(), e.getMessage(), e);
                                } finally {
                                    logger.trace("Thread {} processing cert {} of discovered certificates finalized. Released semaphore.", Thread.currentThread().getName(), index.get());
                                    processCertSemaphore.release();
                                }
                                return null; // Return null to satisfy the return type
                            },
                            executor,
                            MAXIMUM_PARALLELISM
                    )
            );

            // Wait for all tasks to complete
            future.join();
        }

        // Upload certificate keys out of parallel processing to avoid collisions
        for (Map.Entry<PublicKey, List<UUID>> entry : keyToCertificates.entrySet()) {
            try {
                certificateHandler.uploadDiscoveredCertificateKey(entry.getKey(), entry.getValue());
            } catch (NoSuchAlgorithmException e) {
                logger.error("Could not create public key for certificates with UUIDs {}: {}", e.getMessage(), entry.getValue());
            }
        }

        // trigger other events
        eventProducer.produceMessage(DiscoveryFinishedEventHandler.constructEventMessage(discovery.getUuid(), context.getUserUuid(), context.getScheduledJobInfo(), new DiscoveryResult(DiscoveryStatus.PROCESSING, originalMessage)));
        validationProducer.produceMessage(new ValidationMessage(Resource.CERTIFICATE, null, discovery.getUuid(), discovery.getName(), null, null));
    }

    private void processDiscoveredCertificate(EventContext<Certificate> eventContext, int certIndex, int totalCount, DiscoveryHistory discovery, DiscoveryCertificate discoveryCertificate, ConcurrentMap<PublicKey, List<UUID>> keysToCertificatesMap) {
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

                certificateHandler.updateDiscoveredCertificate(discovery, certificate, discoveryCertificate.getMeta());
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

        logger.debug("Finalize processing discovered certificate: {}", certificate.toStringShort());
    }

    public static EventMessage constructEventMessage(UUID discoveryUuid, UUID userUuid, ScheduledJobInfo scheduledJobInfo) {
        return new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.CERTIFICATE, null, Resource.DISCOVERY, discoveryUuid, null, userUuid, scheduledJobInfo);
    }
}
