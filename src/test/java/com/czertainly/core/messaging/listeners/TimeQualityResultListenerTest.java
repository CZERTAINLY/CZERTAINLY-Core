package com.czertainly.core.messaging.listeners;

import com.czertainly.api.model.messaging.timequality.LeapSecondWarningMessage;
import com.czertainly.api.model.messaging.timequality.NtpServerMessage;
import com.czertainly.api.model.messaging.timequality.TimeQualityResultMessage;
import com.czertainly.api.model.messaging.timequality.TimeQualityStatusMessage;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.model.signing.timequality.ExplicitTimeQualityConfiguration;
import com.czertainly.core.model.signing.timequality.ExplicitTimeQualityConfigurationBuilder;
import com.czertainly.core.service.tsa.timequality.TimeQualityRegister;
import com.czertainly.core.service.tsa.timequality.TimeQualityStatus;
import com.czertainly.core.util.BaseRabbitMQIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TimeQualityResultListenerTest extends BaseRabbitMQIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TimeQualityConfigurationRepository configRepository;

    @Autowired
    private TimeQualityRegister timeQualityRegister;

    @Test
    void whenResultReceived_withKnownProfileId_thenRegisterReturnsOkStatus() {
        TimeQualityConfiguration saved = saveConfig("ok-profile");

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_NAME,
                RabbitMQConstants.TIME_QUALITY_RESULTS_ROUTING_KEY,
                buildResultMessage(saved.getUuid(), saved.getName(), TimeQualityStatusMessage.OK));

        ExplicitTimeQualityConfiguration model = modelFor(saved);
        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(timeQualityRegister.getStatus(model)).isEqualTo(TimeQualityStatus.OK));
    }

    @Test
    void whenResultReceived_withDegradedStatus_thenRegisterReturnsDegradedStatus() {
        TimeQualityConfiguration saved = saveConfig("degraded-profile");

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_NAME,
                RabbitMQConstants.TIME_QUALITY_RESULTS_ROUTING_KEY,
                buildResultMessage(saved.getUuid(), saved.getName(), TimeQualityStatusMessage.DEGRADED));

        ExplicitTimeQualityConfiguration model = modelFor(saved);
        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(timeQualityRegister.getStatus(model)).isEqualTo(TimeQualityStatus.DEGRADED));
    }

    @Test
    void whenResultReceived_withUnknownProfileId_thenResultIsDropped() {
        UUID unknownId = UUID.randomUUID();

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_NAME,
                RabbitMQConstants.TIME_QUALITY_RESULTS_ROUTING_KEY,
                buildResultMessage(unknownId, "unknown", TimeQualityStatusMessage.OK));

        // Wait long enough for the listener to have processed the message, then assert it was dropped.
        // A dropped result leaves the register empty for this UUID, so getStatus returns DEGRADED
        // ("no result received yet") — the same state as before the message was sent.
        ExplicitTimeQualityConfiguration model = ExplicitTimeQualityConfigurationBuilder
                .anExplicitTimeQualityConfiguration()
                .withDefaults()
                .uuid(unknownId)
                .name("unknown")
                .build();

        await().pollDelay(1, SECONDS).atMost(3, SECONDS).untilAsserted(() ->
                assertThat(timeQualityRegister.getStatus(model)).isEqualTo(TimeQualityStatus.DEGRADED));
    }

    private TimeQualityResultMessage buildResultMessage(UUID id, String name, TimeQualityStatusMessage status) {
        NtpServerMessage server = new NtpServerMessage();
        server.setHost("pool.ntp.org");
        server.setReachable(true);
        server.setOffsetMs(0.5);
        server.setRttMs(2.0);
        server.setStratum(2);
        server.setPrecisionMs(0.1);

        TimeQualityResultMessage msg = new TimeQualityResultMessage();
        msg.setId(id);
        msg.setName(name);
        msg.setTimestamp(Instant.now());
        msg.setStatus(status);
        msg.setMeasuredDriftMs(0.0);
        msg.setReachableServers(1);
        msg.setLeapSecondWarning(LeapSecondWarningMessage.NONE);
        msg.setServers(List.of(server));
        return msg;
    }

    private ExplicitTimeQualityConfiguration modelFor(TimeQualityConfiguration entity) {
        return ExplicitTimeQualityConfigurationBuilder
                .anExplicitTimeQualityConfiguration()
                .uuid(entity.getUuid())
                .name(entity.getName())
                .accuracy(Duration.ofMinutes(5))
                .ntpServers(List.of("pool.ntp.org"))
                .ntpCheckInterval(Duration.ofSeconds(30))
                .ntpSamplesPerServer(4)
                .ntpCheckTimeout(Duration.ofSeconds(5))
                .ntpServersMinReachable(1)
                .maxClockDrift(Duration.ofSeconds(10))
                .leapSecondGuard(false)
                .build();
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
