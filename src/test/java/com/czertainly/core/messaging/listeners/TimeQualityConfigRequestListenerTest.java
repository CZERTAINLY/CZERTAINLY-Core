package com.czertainly.core.messaging.listeners;

import com.czertainly.api.model.messaging.timequality.TimeQualityConfigRequestMessage;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimeQualityConfigRequestListenerTest extends BaseRabbitMQIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TimeQualityConfigurationRepository configRepository;

    @Test
    void whenConfigRequestReceived_withConfigsInDb_thenSnapshotContainsAllConfigs() {
        TimeQualityConfiguration first  = saveConfig("profile-a");
        TimeQualityConfiguration second = saveConfig("profile-b");

        sendConfigRequest();

        var snapshot = receiveSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getConfigurations()).hasSize(2);

        List<UUID> publishedIds = snapshot.getConfigurations().stream()
                .map(c -> c.getId())
                .toList();
        assertThat(publishedIds).containsExactlyInAnyOrder(first.getUuid(), second.getUuid());
    }

    @Test
    void whenConfigRequestReceived_withEmptyDb_thenEmptySnapshotPublished() {
        sendConfigRequest();

        var snapshot = receiveSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getConfigurations()).isEmpty();
    }

    private void sendConfigRequest() {
        TimeQualityConfigRequestMessage request = new TimeQualityConfigRequestMessage();
        request.setRequestedAt(Instant.now());
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_NAME,
                RabbitMQConstants.TIME_QUALITY_CONFIG_REQUEST_ROUTING_KEY,
                request);
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
