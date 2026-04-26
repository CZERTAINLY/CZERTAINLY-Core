package com.czertainly.core.messaging.producers;

import com.czertainly.api.model.messaging.timequality.TimeQualityConfigSnapshotMessage;
import com.czertainly.api.model.messaging.timequality.TimeQualityConfigurationMessage;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.TimeQualityConfigChangedEvent;
import com.czertainly.core.messaging.producers.TimeQualityMonitorInitializer;
import com.czertainly.core.util.BaseRabbitMQIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimeQualityMonitorInitializerTest extends BaseRabbitMQIntegrationTest {

    @Autowired
    private TimeQualityMonitorInitializer initializer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TimeQualityConfigurationRepository configRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void whenOnApplicationReady_withConfigsInDb_thenSnapshotContainsAllConfigs() {
        TimeQualityConfiguration first  = saveConfig("startup-profile-a");
        TimeQualityConfiguration second = saveConfig("startup-profile-b");

        initializer.onApplicationReady();

        var snapshot = receiveSnapshot();
        assertThat(snapshot).isNotNull();

        List<UUID> publishedIds = snapshot.getConfigurations().stream()
                .map(TimeQualityConfigurationMessage::getId)
                .toList();
        assertThat(publishedIds).containsExactlyInAnyOrder(first.getUuid(), second.getUuid());
    }

    @Test
    void whenOnApplicationReady_withEmptyDb_thenEmptySnapshotPublished() {
        initializer.onApplicationReady();

        var snapshot = receiveSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getConfigurations()).isEmpty();
    }

    @Test
    void whenConfigChangedEventPublished_thenSnapshotRepublished() {
        saveConfig("changed-profile");

        // @TransactionalEventListener fires only after a transaction commits, so we need an explicit one here
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new TimeQualityConfigChangedEvent(this));
            return null;
        });

        var snapshot = receiveSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getConfigurations()).hasSize(1);
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
