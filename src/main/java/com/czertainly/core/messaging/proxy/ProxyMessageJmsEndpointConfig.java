package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyMessage;
import com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.stereotype.Component;

/**
 * JMS endpoint configuration for receiving proxy messages.
 *
 * <p>Configures a listener that subscribes to proxy messages.
 * For Azure ServiceBus, filtering is configured via subscription filters in Azure.</p>
 */
@Component
@Profile("!test")
@AllArgsConstructor
public class ProxyMessageJmsEndpointConfig extends AbstractJmsEndpointConfig<ProxyMessage> {

    private final ProxyProperties proxyProperties;

    @Override
    public SimpleJmsListenerEndpoint listenerEndpoint() {
        return listenerEndpointInternal(
                () -> "proxyMessageListener",
                proxyProperties::exchange,
                proxyProperties::responseQueue,
                () -> "1",
                ProxyMessage.class
        );
    }

}
