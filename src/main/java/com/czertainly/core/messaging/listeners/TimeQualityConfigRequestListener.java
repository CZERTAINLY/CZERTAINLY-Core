package com.czertainly.core.messaging.listeners;

import com.czertainly.api.model.messaging.timequality.TimeQualityConfigRequestMessage;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.producers.TimeQualityConfigurationProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class TimeQualityConfigRequestListener {
    private TimeQualityConfigurationRepository timeQualityConfigurationRepository;
    private TimeQualityConfigurationProducer timeQualityConfigurationProducer;

    @Autowired
    public void setTimeQualityConfigurationRepository(TimeQualityConfigurationRepository timeQualityConfigurationRepository) {
        this.timeQualityConfigurationRepository = timeQualityConfigurationRepository;
    }

    @Autowired
    public void setTimeQualityConfigurationProducer(TimeQualityConfigurationProducer timeQualityConfigurationProducer) {
        this.timeQualityConfigurationProducer = timeQualityConfigurationProducer;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_TIME_QUALITY_CONFIG_REQUEST, messageConverter = "jsonMessageConverter", concurrency = "1")
    public void processMessage(TimeQualityConfigRequestMessage message) {
        log.debug("Received time quality config request (requestedAt={})", message.getRequestedAt());
        timeQualityConfigurationProducer.publishSnapshot(timeQualityConfigurationRepository.findAll());
    }
}
