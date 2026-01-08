package com.czertainly.core.messaging.jms.listeners.validation;

import com.czertainly.core.messaging.jms.configuration.MessagingConcurrencyProperties;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig;
import com.czertainly.core.messaging.model.ValidationMessage;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@AllArgsConstructor
public class ValidationJmsEndpointConfig extends AbstractJmsEndpointConfig<ValidationMessage> {

    private final MessagingProperties messagingProperties;
    private final MessagingConcurrencyProperties messagingConcurrencyProperties;

    public SimpleJmsListenerEndpoint listenerEndpoint() {
        return listenerEndpointInternal(
                () -> "validationListener",
                () -> messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS
                    ? messagingProperties.exchange()
                    : messagingProperties.consumerDestination(messagingProperties.queue().validation()),
                () -> messagingProperties.routingKey().validation(),
                messagingConcurrencyProperties::validation,
                ValidationMessage.class
        );
    }
}
