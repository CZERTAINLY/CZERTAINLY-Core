package com.czertainly.core.messaging.jms.listeners.notification;

import com.czertainly.core.messaging.jms.configuration.MessagingConcurrencyProperties;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig;
import com.czertainly.core.messaging.model.NotificationMessage;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@AllArgsConstructor
public class NotificationJmsEndpointConfig extends AbstractJmsEndpointConfig<NotificationMessage> {

    private final MessagingProperties messagingProperties;
    private final MessagingConcurrencyProperties messagingConcurrencyProperties;

    @Override
    public SimpleJmsListenerEndpoint listenerEndpoint() {
        return listenerEndpointInternal(
                () -> "notificationListener",
                () -> messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS
                    ? messagingProperties.exchange()
                    : messagingProperties.consumerDestination(messagingProperties.queue().notification()),
                () -> messagingProperties.routingKey().notification(),
                messagingConcurrencyProperties::notifications,
                NotificationMessage.class
        );
    }
}
