package com.czertainly.core.messaging.producers;

import com.czertainly.api.model.messaging.timequality.TimeQualityConfigSnapshotMessage;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.util.BaseRabbitMQIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeQualityConfigurationProducerTest extends BaseRabbitMQIntegrationTest {

    @Autowired
    private TimeQualityConfigurationProducer producer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TimeQualityConfigurationRepository configRepository;

    @Test
    void whenPublishSnapshot_withConfigurations_thenMessageArrivesOnConfigQueue() {
        TimeQualityConfiguration saved = saveConfig("ntp-profile");

        producer.publishSnapshot(List.of(saved));

        var msg = receiveSnapshot();
        assertThat(msg).isNotNull();
        assertThat(msg.getConfigurations()).hasSize(1);
        assertThat(msg.getConfigurations().getFirst().getId()).isEqualTo(saved.getUuid());
        assertThat(msg.getConfigurations().getFirst().getName()).isEqualTo("ntp-profile");
        assertThat(msg.getConfigurations().getFirst().getNtpServers()).containsExactly("pool.ntp.org");
        assertThat(msg.getConfigurations().getFirst().isLeapSecondGuard()).isFalse();
    }

    @Test
    void whenPublishSnapshot_withEmptyList_thenEmptySnapshotArrivesOnConfigQueue() {
        producer.publishSnapshot(List.of());

        var msg = receiveSnapshot();
        assertThat(msg).isNotNull();
        assertThat(msg.getConfigurations()).isEmpty();
    }

    @Test
    void whenPublishSnapshot_thenGeneratedAtIsRecent() {
        Instant before = Instant.now();
        producer.publishSnapshot(List.of());
        Instant after = Instant.now();

        var msg = receiveSnapshot();
        assertThat(msg).isNotNull();
        assertThat(msg.getGeneratedAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    private TimeQualityConfigSnapshotMessage receiveSnapshot() {
        return (TimeQualityConfigSnapshotMessage) rabbitTemplate
                .receiveAndConvert(RabbitMQConstants.QUEUE_TIME_QUALITY_CONFIG, 5_000);
    }

    private TimeQualityConfiguration saveConfig(String name) {
        var config = new TimeQualityConfiguration();
        config.setName(name);
        config.setNtpServers(List.of("pool.ntp.org"));
        config.setNtpCheckInterval(Duration.ofSeconds(30));
        config.setNtpSamplesPerServer(4);
        config.setNtpCheckTimeout(Duration.ofSeconds(5));
        config.setNtpServersMinReachable(1);
        config.setMaxClockDrift(Duration.ofSeconds(1));
        config.setLeapSecondGuard(false);
        return configRepository.save(config);
    }
}
