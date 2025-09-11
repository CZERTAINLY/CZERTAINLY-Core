package com.czertainly.core.messaging.listeners;

import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.AuditLogMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AuditLogsListener {

    @RabbitListener(queues = RabbitMQConstants.QUEUE_AUDIT_LOGS_NAME, messageConverter = "jsonMessageConverter", concurrency = "${messaging.concurrency.audit-logs}")
    public void processMessage(final AuditLogMessage auditLogMessage) {

    }

}
