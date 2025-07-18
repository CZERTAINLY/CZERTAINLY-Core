package com.czertainly.core.messaging.listeners;

import com.czertainly.api.exception.EventException;
import com.czertainly.core.events.IEventHandler;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.util.AuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class EventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    private AuthHelper authHelper;

    private Map<String, IEventHandler> eventHandlers;

    @Autowired
    public void setAuthHelper(AuthHelper authHelper) {
        this.authHelper = authHelper;
    }

    @Autowired
    public void setEventHandlers(Map<String, IEventHandler> eventHandlers) {
        this.eventHandlers = eventHandlers;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_EVENTS_NAME, messageConverter = "jsonMessageConverter", concurrency = "${messaging.concurrency.events}")
    public void processMessage(EventMessage eventMessage) {
        if (eventMessage.getUserUuid() != null) {
            authHelper.authenticateAsUser(eventMessage.getUserUuid());
        }

        IEventHandler eventHandler = eventHandlers.get(eventMessage.getEvent().getCode());
        try {
            eventHandler.handleEvent(eventMessage);
        } catch (EventException e) {
            logger.error("Error in handling event {}: {}. Message: {}", eventMessage.getEvent().getLabel(), e.getMessage(), eventMessage);
        }
    }

}
