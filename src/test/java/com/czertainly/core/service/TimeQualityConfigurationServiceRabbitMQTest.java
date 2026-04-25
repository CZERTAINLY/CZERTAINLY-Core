package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationRequestDto;
import com.czertainly.api.model.messaging.timequality.TimeQualityConfigSnapshotMessage;
import com.czertainly.api.model.messaging.timequality.TimeQualityConfigurationMessage;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.util.BaseRabbitMQIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These tests verify that every CRUD operation on TimeQualityConfiguration triggers a fresh configuration snapshot
 * published to the time-quality.config queue.
 */
class TimeQualityConfigurationServiceRabbitMQTest extends BaseRabbitMQIntegrationTest {

    @Autowired
    private TimeQualityConfigurationService timeQualityConfigurationService;

    @Autowired
    private TimeQualityConfigurationRepository configRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void whenConfigCreated_thenSnapshotPublishedToConfigQueue()
            throws AlreadyExistException, AttributeException, NotFoundException {
        timeQualityConfigurationService.createTimeQualityConfiguration(buildRequest("new-profile"));

        var snapshot = receiveSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getConfigurations())
                .extracting(TimeQualityConfigurationMessage::getName)
                .contains("new-profile");
    }

    @Test
    void whenConfigUpdated_thenSnapshotRepublished()
            throws AlreadyExistException, AttributeException, NotFoundException {
        TimeQualityConfiguration saved = saveConfig("update-profile");

        timeQualityConfigurationService.updateTimeQualityConfiguration(
                SecuredUUID.fromUUID(saved.getUuid()), buildRequest("update-profile-renamed"));

        var snapshot = receiveSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getConfigurations())
                .extracting(TimeQualityConfigurationMessage::getName)
                .contains("update-profile-renamed");
    }

    @Test
    void whenConfigDeleted_thenSnapshotRepublished()
            throws NotFoundException {
        TimeQualityConfiguration saved = saveConfig("delete-profile");

        timeQualityConfigurationService.deleteTimeQualityConfiguration(
                SecuredUUID.fromUUID(saved.getUuid()));

        var snapshot = receiveSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getConfigurations())
                .extracting(TimeQualityConfigurationMessage::getId)
                .doesNotContain(saved.getUuid());
    }

    @Test
    void whenBulkDeleted_thenExactlyOneSnapshotPublished() {
        TimeQualityConfiguration a = saveConfig("bulk-a");
        TimeQualityConfiguration b = saveConfig("bulk-b");
        TimeQualityConfiguration c = saveConfig("bulk-c");

        timeQualityConfigurationService.bulkDeleteTimeQualityConfigurations(List.of(
                SecuredUUID.fromUUID(a.getUuid()),
                SecuredUUID.fromUUID(b.getUuid()),
                SecuredUUID.fromUUID(c.getUuid())));

        var first = receiveSnapshot();
        assertThat(first).isNotNull();

        // Only one snapshot should have been published (not one per deleted entity)
        var second = (TimeQualityConfigSnapshotMessage) rabbitTemplate
                .receiveAndConvert(RabbitMQConstants.QUEUE_TIME_QUALITY_CONFIG, 1_000);
        assertThat(second).isNull();
    }

    private TimeQualityConfigSnapshotMessage receiveSnapshot() {
        return (TimeQualityConfigSnapshotMessage) rabbitTemplate
                .receiveAndConvert(RabbitMQConstants.QUEUE_TIME_QUALITY_CONFIG, 5_000);
    }

    private TimeQualityConfigurationRequestDto buildRequest(String name) {
        TimeQualityConfigurationRequestDto req = new TimeQualityConfigurationRequestDto();
        req.setName(name);
        req.setAccuracy(Duration.ofSeconds(1));
        req.setNtpServers(List.of("pool.ntp.org"));
        req.setNtpCheckInterval(Duration.ofSeconds(30));
        req.setNtpSamplesPerServer(4);
        req.setNtpCheckTimeout(Duration.ofSeconds(5));
        req.setNtpServersMinReachable(1);
        req.setMaxClockDrift(Duration.ofSeconds(1));
        req.setLeapSecondGuard(false);
        return req;
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
