package com.otilm.core.integration.config;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.converter.ObjectToJsonConverter;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.ScheduledJob;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.dao.repository.ScheduledJobsRepository;
import com.otilm.core.util.BaseSpringBootTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proof that {@code JsonColumnFormatMapperConfig} reaches Hibernate and shapes what every
 * {@code @JdbcTypeCode(SqlTypes.JSON)} column stores. {@link Discovery} is the vehicle for the persisted-shape case,
 * {@link ScheduledJob} for the columns that a converter keeps out of the stated mapper's reach.
 */
@Transactional
class JsonColumnFormatMapperITest extends BaseSpringBootTest {

    @Autowired
    private DiscoveryRepository discoveryRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JacksonJsonFormatMapper jsonColumnFormatMapper;
    @Autowired
    private ScheduledJobsRepository scheduledJobsRepository;

    /**
     * Hibernate must use the stated mapper. The stored bytes cannot show this on their own, because a fallback mapper
     * writes the same output for the payload this test stores.
     */
    @Test
    void hibernateUsesTheStatedJsonFormatMapper() {
        FormatMapper active = entityManager
                .getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class)
                .getSessionFactoryOptions()
                .getJsonFormatMapper();

        assertThat(active).isSameAs(jsonColumnFormatMapper);
    }

    /**
     * Pins the persisted shape: a null map value reaches the column. The payload is a plain {@code Map} because
     * inclusion declared on a DTO outranks the mapper.
     */
    @Test
    void jsonColumnsKeepNullMembers() {
        Map<String, Object> metaWithUnsetMember = new HashMap<>();
        metaWithUnsetMember.put("connectorRunId", "run-42");
        metaWithUnsetMember.put("connectorBuild", null);

        Discovery run = new Discovery();
        run.setName("mapper-proof");
        run.setKind("IP-HostName");
        run.setStatus(DiscoveryStatus.IN_PROGRESS);
        run.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
        run.setConnectorUuid(UUID.randomUUID());
        run.setConnectorName("network-discovery");
        run.setRunMeta(metaWithUnsetMember);
        UUID runUuid = discoveryRepository.saveAndFlush(run).getUuid();
        entityManager.clear();

        String storedJson = (String) entityManager
                .createNativeQuery("SELECT run_meta::text FROM discovery WHERE uuid = :uuid")
                .setParameter("uuid", runUuid)
                .getSingleResult();

        assertThat(storedJson)
                .describedAs("the null member must reach the column, which is what Jackson's default inclusion does")
                .contains("\"connectorBuild\": null")
                .contains("\"connectorRunId\": \"run-42\"");
    }

    /**
     * Marks the boundary of what the stated mapper governs. A column carrying {@link ObjectToJsonConverter} alongside
     * {@code @JdbcTypeCode(SqlTypes.JSON)} is serialized by the converter's wire mapper instead: Hibernate resolves the
     * JPA converter, which makes the relational type {@code String}, and a {@code String} reaches the driver verbatim.
     * The wire mapper's {@code NON_NULL} is therefore visible here and nowhere else in this class.
     */
    @Test
    void convertedColumnsAreSerializedByTheConverterNotTheStatedMapper() {
        Map<String, Object> payloadWithUnsetMember = new HashMap<>();
        payloadWithUnsetMember.put("resourceUuid", "run-42");
        payloadWithUnsetMember.put("cancelledAt", null);

        ScheduledJob job = new ScheduledJob();
        job.setJobName("mapper-proof-" + UUID.randomUUID());
        job.setJobClassName("com.otilm.core.tasks.DiscoveryCertificateTask");
        job.setCronExpression("0 0 * * * *");
        job.setObjectData(payloadWithUnsetMember);
        UUID jobUuid = scheduledJobsRepository.saveAndFlush(job).getUuid();
        entityManager.clear();

        String storedJson = (String) entityManager
                .createNativeQuery("SELECT object_data::text FROM scheduled_job WHERE uuid = :uuid")
                .setParameter("uuid", jobUuid)
                .getSingleResult();

        assertThat(storedJson)
                .describedAs(
                        "the converter's wire mapper omits nulls, so this column does not follow the stated mapper")
                .contains("\"resourceUuid\": \"run-42\"")
                .doesNotContain("cancelledAt");
    }
}
