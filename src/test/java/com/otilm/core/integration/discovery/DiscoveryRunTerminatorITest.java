package com.otilm.core.integration.discovery;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.service.handler.discovery.DiscoveryRunTerminator;
import com.otilm.core.util.BaseSpringBootTest;
import com.otilm.core.util.DiscoveryRunMetaFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ending a run is a decision taken under the row lock, and it is only as good as what the locked read returns.
 *
 * <p>
 * A lifecycle call runs {@code NOT_SUPPORTED} with the run already loaded and then spends a connector call — tens of
 * seconds — outside any transaction. Whatever ended the run in that window is invisible to a locking read that answers
 * from the persistence context the caller already populated.
 */
class DiscoveryRunTerminatorITest extends BaseSpringBootTest {

    @Autowired
    private DiscoveryRunTerminator terminator;
    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void aRunEndedWhileTheCallerWasAtTheConnector_isNotEndedASecondTime() {
        Discovery run = v2Run();
        UUID uuid = run.getUuid();

        // Bind one EntityManager to the thread, which is what open-in-view does for every HTTP request and what
        // makes this reachable: a REQUIRES_NEW transaction joins that same persistence context rather than opening
        // its own, so a locking read inside it answers from the cache the caller already populated.
        EntityManager bound = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(bound));
        boolean endedByUs;
        try {
            // Stand in for the lifecycle path: load the run, as getDiscoveryEntity does, and hold it.
            Discovery held = bound.find(Discovery.class, uuid);
            assertThat(held.getStatus()).isEqualTo(DiscoveryStatus.IN_PROGRESS);

            // A status tick ends the run while the caller is at the connector. Written around JPA so the caller's
            // persistence context cannot learn of it -- which is exactly the situation a stale read reproduces.
            endItBehindTheCaller(uuid);

            endedByUs = terminator
                    .endWith(uuid, r -> new DiscoveryRunTerminator.Ending(DiscoveryStatus.CANCELLED, "cancelled"));
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            bound.close();
        }

        assertThat(endedByUs)
                .as("the run was already terminal; ending it again finalizes one run twice and announces it twice")
                .isFalse();
        assertThat(discoveryRepository.findByUuid(uuid).orElseThrow().getStatus())
                .as("the first ending stands")
                .isEqualTo(DiscoveryStatus.FAILED);
    }

    private void endItBehindTheCaller(UUID uuid) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template
                .executeWithoutResult(status -> entityManager
                        .createNativeQuery("UPDATE discovery SET status = :status WHERE uuid = :uuid")
                        // EnumType.STRING: the column holds the constant name, not the wire code.
                        .setParameter("status", DiscoveryStatus.FAILED.name())
                        .setParameter("uuid", uuid)
                        .executeUpdate());
    }

    private Discovery v2Run() {
        Discovery run = new Discovery();
        run.setName("v2-scan-" + UUID.randomUUID());
        run.setKind("IP-HostName");
        run.setStatus(DiscoveryStatus.IN_PROGRESS);
        run.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
        run.setConnectorUuid(UUID.randomUUID());
        run.setConnectorName("network-discovery");
        run.setConnectorInterfaceUuid(UUID.randomUUID());
        run.setRunMeta(DiscoveryRunMetaFixture.runMeta("connectorRunId", "run-42"));
        return discoveryRepository.saveAndFlush(run);
    }
}
