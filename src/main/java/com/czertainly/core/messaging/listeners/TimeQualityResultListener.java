package com.czertainly.core.messaging.listeners;

import com.czertainly.api.model.messaging.timequality.LeapSecondWarningMessage;
import com.czertainly.api.model.messaging.timequality.NtpServerMessage;
import com.czertainly.api.model.messaging.timequality.TimeQualityResultMessage;
import com.czertainly.api.model.messaging.timequality.TimeQualityStatusMessage;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.tsa.timequality.LeapSecondWarning;
import com.czertainly.core.service.tsa.timequality.NtpServerResult;
import com.czertainly.core.service.tsa.timequality.TimeQualityRegister;
import com.czertainly.core.service.tsa.timequality.TimeQualityResult;
import com.czertainly.core.service.tsa.timequality.TimeQualityStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class TimeQualityResultListener {
    private TimeQualityConfigurationRepository timeQualityConfigurationRepository;
    private TimeQualityRegister timeQualityRegister;

    @Autowired
    public void setTimeQualityConfigurationRepository(TimeQualityConfigurationRepository timeQualityConfigurationRepository) {
        this.timeQualityConfigurationRepository = timeQualityConfigurationRepository;
    }

    @Autowired
    public void setTimeQualityRegister(TimeQualityRegister timeQualityRegister) {
        this.timeQualityRegister = timeQualityRegister;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_TIME_QUALITY_RESULTS, messageConverter = "jsonMessageConverter", concurrency = "1")
    public void processMessage(TimeQualityResultMessage message) {
        if (timeQualityConfigurationRepository.findByUuid(SecuredUUID.fromUUID(message.getId())).isEmpty()) {
            log.warn("Received time quality result for unknown profile ID={}, dropping", message.getId());
            return;
        }

        TimeQualityResult result = new TimeQualityResult(
                message.getId(),
                message.getName(),
                message.getTimestamp(),
                toStatus(message.getStatus()),
                message.getMeasuredDriftMs(),
                message.getReachableServers(),
                message.getReason(),
                toLeapSecondWarning(message.getLeapSecondWarning()),
                toNtpServerResults(message.getServers())
        );
        log.debug("Received time quality result {}", result);
        timeQualityRegister.update(result);
    }

    private static TimeQualityStatus toStatus(TimeQualityStatusMessage status) {
        return switch (status) {
            case OK -> TimeQualityStatus.OK;
            case DEGRADED -> TimeQualityStatus.DEGRADED;
        };
    }

    private static LeapSecondWarning toLeapSecondWarning(LeapSecondWarningMessage warning) {
        return switch (warning) {
            case NONE -> LeapSecondWarning.NONE;
            case POSITIVE -> LeapSecondWarning.POSITIVE;
            case NEGATIVE -> LeapSecondWarning.NEGATIVE;
        };
    }

    private static List<NtpServerResult> toNtpServerResults(List<NtpServerMessage> servers) {
        if (servers == null) return List.of();
        return servers.stream()
                .map(s -> new NtpServerResult(s.getHost(), s.isReachable(), s.getOffsetMs(), s.getRttMs(), s.getStratum(), s.getPrecisionMs()))
                .toList();
    }
}
