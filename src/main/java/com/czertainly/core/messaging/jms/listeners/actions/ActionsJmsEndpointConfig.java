package com.czertainly.core.messaging.jms.listeners.actions;

import com.czertainly.core.messaging.jms.configuration.MessagingConcurrencyProperties;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig;
import com.czertainly.core.messaging.model.ActionMessage;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.stereotype.Component;


@Component
@Profile("!test")
@AllArgsConstructor
public class ActionsJmsEndpointConfig extends AbstractJmsEndpointConfig<ActionMessage> {

    private final MessagingConcurrencyProperties messagingConcurrencyProperties;

    @Override
    public SimpleJmsListenerEndpoint listenerEndpoint() {
        return listenerEndpointInternal(
                () -> "actionsListener",
                () -> messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS
                    ? messagingProperties.exchange()
                    : messagingProperties.consumerDestination(messagingProperties.queue().actions()),
                () -> messagingProperties.routingKey().actions(),
                messagingConcurrencyProperties::actions,
                ActionMessage.class
        );
    }
}
