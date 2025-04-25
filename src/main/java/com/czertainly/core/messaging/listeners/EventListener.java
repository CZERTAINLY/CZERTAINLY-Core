package com.czertainly.core.messaging.listeners;

import com.czertainly.api.exception.EventException;
import com.czertainly.core.events.IEventHandler;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@Transactional
public class EventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    private Map<String, IEventHandler> eventHandlers;

    @Autowired
    public void setEventHandlers(Map<String, IEventHandler> eventHandlers) {
        this.eventHandlers = eventHandlers;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_EVENTS_NAME, messageConverter = "jsonMessageConverter", concurrency = "3")
    public void processMessage(EventMessage eventMessage) {
        IEventHandler eventHandler = eventHandlers.get(eventMessage.getResourceEvent().getCode());
        try {
            eventHandler.handleEvent(eventMessage);
        } catch (EventException e) {
            logger.error("Error in handling event {}: {}. Message: {}", eventMessage.getResourceEvent().getLabel(), e.getMessage(), eventMessage);
        }
    }

}
