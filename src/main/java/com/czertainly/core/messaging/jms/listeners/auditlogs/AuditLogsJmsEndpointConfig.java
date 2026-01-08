package com.czertainly.core.messaging.jms.listeners.auditlogs;

import com.czertainly.core.messaging.jms.configuration.MessagingConcurrencyProperties;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig;
import com.czertainly.core.messaging.model.AuditLogMessage;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.stereotype.Component;


@Component
@Profile("!test")
@AllArgsConstructor
public class AuditLogsJmsEndpointConfig extends AbstractJmsEndpointConfig<AuditLogMessage> {
    private final MessagingProperties messagingProperties;
    private final MessagingConcurrencyProperties messagingConcurrencyProperties;

    @Override
    public SimpleJmsListenerEndpoint listenerEndpoint() {
        return listenerEndpointInternal(
                () -> "auditLogsListener",
                () -> messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS
                    ? messagingProperties.exchange()
                    : messagingProperties.consumerDestination(messagingProperties.queue().auditLogs()),
                () -> messagingProperties.routingKey().auditLogs(),
                messagingConcurrencyProperties::auditLogs,
                AuditLogMessage.class
        );
    }
}
