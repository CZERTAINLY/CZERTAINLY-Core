package com.czertainly.core.messaging.producers;

import com.czertainly.api.model.messaging.timequality.TimeQualityConfigSnapshotMessage;
import com.czertainly.api.model.messaging.timequality.TimeQualityConfigurationMessage;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class TimeQualityConfigurationProducer {
    private RabbitTemplate rabbitTemplate;

    @Autowired
    public void setRabbitTemplate(final RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setMandatory(true);
        this.rabbitTemplate.setReturnsCallback(returned
                -> log.debug("Failed to deliver message to exchange {} with routing key {}: {}. Time Quality Monitor is not running?",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }

    public void publishSnapshot(List<TimeQualityConfiguration> configurations) {
        TimeQualityConfigSnapshotMessage message = new TimeQualityConfigSnapshotMessage();
        message.setGeneratedAt(Instant.now());
        message.setConfigurations(configurations.stream().map(this::toMessage).toList());
        log.debug("Publishing time quality config snapshot with {} configurations", message.getConfigurations().size());
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.TIME_QUALITY_CONFIG_ROUTING_KEY, message);
    }

    private TimeQualityConfigurationMessage toMessage(TimeQualityConfiguration config) {
        TimeQualityConfigurationMessage msg = new TimeQualityConfigurationMessage();
        msg.setId(config.getUuid());
        msg.setName(config.getName());
        msg.setNtpServers(config.getNtpServers());
        msg.setNtpCheckInterval(config.getNtpCheckInterval());
        msg.setNtpSamplesPerServer(Objects.requireNonNullElse(config.getNtpSamplesPerServer(), 0));
        msg.setNtpCheckTimeout(config.getNtpCheckTimeout());
        msg.setNtpServersMinReachable(Objects.requireNonNullElse(config.getNtpServersMinReachable(), 0));
        msg.setMaxClockDrift(config.getMaxClockDrift());
        msg.setLeapSecondGuard(Boolean.TRUE.equals(config.getLeapSecondGuard()));
        return msg;
    }
}
