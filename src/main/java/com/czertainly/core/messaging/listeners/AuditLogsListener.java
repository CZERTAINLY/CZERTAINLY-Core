package com.czertainly.core.messaging.listeners;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.records.*;
import com.czertainly.core.logging.AuditLogEnhancer;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.AuditLogMessage;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.service.ResourceService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@Transactional
public class AuditLogsListener {

    private AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private AuditLogEnhancer auditLogEnhancer;

    @Autowired
    public void setAuditLogEnhancer(AuditLogEnhancer auditLogEnhancer) {
        this.auditLogEnhancer = auditLogEnhancer;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_AUDIT_LOGS_NAME, messageConverter = "jsonMessageConverter", concurrency = "${messaging.concurrency.audit-logs}")
    public void processMessage(final AuditLogMessage auditLogMessage) {

        LogRecord logRecord = auditLogMessage.getLogRecord();

        LogRecord.LogRecordBuilder builder = LogRecord.builder()
                .audited(true)
                .timestamp(logRecord.timestamp())
                .version(logRecord.version())
                .message(logRecord.message())
                .actor(logRecord.actor())
                .additionalData(logRecord.additionalData())
                .module(logRecord.module())
                .source(logRecord.source())
                .operationData(logRecord.operationData())
                .operation(logRecord.operation())
                .operationResult(logRecord.operationResult())
                .resource(new ResourceRecord(logRecord.resource().type(), auditLogEnhancer.enrichNamesAndUuids(logRecord.resource().objects(), logRecord.resource().type())))
                .affiliatedResource(logRecord.affiliatedResource() == null ? null : new ResourceRecord(logRecord.affiliatedResource().type(), auditLogEnhancer.enrichNamesAndUuids(logRecord.affiliatedResource().objects(), logRecord.affiliatedResource().type())));
        auditLogService.log(builder.build());
    }

}
