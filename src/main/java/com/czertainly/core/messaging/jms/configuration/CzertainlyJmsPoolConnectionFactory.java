package com.czertainly.core.messaging.jms.configuration;

import jakarta.jms.Connection;
import org.apache.qpid.jms.JmsConnection;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.messaginghub.pooled.jms.pool.PooledConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom pool factory that works around a bug in pooled-jms where {@code validateObject()}
 * fails to evict dead Qpid JMS connections.
 *
 * <p>When Azure ServiceBus sends {@code amqp:connection:forced}, Qpid sets
 * {@code failureCause} atomically on the {@link JmsConnection}. However, pooled-jms's
 * {@code validateObject()} only catches {@code IllegalStateException} to detect dead
 * connections. Qpid throws {@code JmsConnectionFailedException} (extends {@code JMSException}),
 * which falls into a generic {@code catch (Exception)} block that assumes the connection
 * is still valid.</p>
 *
 * <p>This subclass overrides {@code isClosed()} on the {@link PooledConnection} to also
 * check {@link JmsConnection#isFailed()} and {@link JmsConnection#isClosed()}, making
 * dead connections visible to the pool's own validation and borrow logic without any
 * external workarounds.</p>
 */
public class CzertainlyJmsPoolConnectionFactory extends JmsPoolConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyJmsPoolConnectionFactory.class);

    @Override
    protected PooledConnection createPooledConnection(Connection connection) {
        return new PooledConnection(connection) {
            @Override
            public boolean isClosed() {
                if (super.isClosed()) {
                    return true;
                }
                try {
                    Connection underlying = getConnection();
                    if (underlying instanceof JmsConnection jmsConnection) {
                        if (jmsConnection.isClosed() || jmsConnection.isFailed()) {
                            logger.warn("Qpid JmsConnection is {} — reporting as closed to trigger pool eviction",
                                    jmsConnection.isFailed() ? "failed" : "closed");
                            return true;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Unexpected exception inspecting underlying JmsConnection state", e);
                }
                return false;
            }
        };
    }
}
