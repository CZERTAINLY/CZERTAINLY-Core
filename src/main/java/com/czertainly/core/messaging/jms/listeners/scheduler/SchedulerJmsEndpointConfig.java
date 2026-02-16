package com.czertainly.core.messaging.jms.listeners.scheduler;

import com.czertainly.api.model.scheduler.SchedulerJobExecutionMessage;
import com.czertainly.core.messaging.jms.configuration.MessagingConcurrencyProperties;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@AllArgsConstructor
public class SchedulerJmsEndpointConfig extends AbstractJmsEndpointConfig<SchedulerJobExecutionMessage> {

    private final MessagingProperties messagingProperties;
    private final MessagingConcurrencyProperties messagingConcurrencyProperties;

    public SimpleJmsListenerEndpoint listenerEndpoint() {
        return listenerEndpointInternal(
                () -> "schedulerListener",
                () -> messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS
                    ? messagingProperties.exchange()
                    : messagingProperties.consumerDestination(messagingProperties.queue().scheduler()),
                () -> messagingProperties.routingKey().scheduler(),
                messagingConcurrencyProperties::scheduler,
                SchedulerJobExecutionMessage.class
        );
    }
}
