package com.czertainly.core.messaging.listeners;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.records.*;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.AuditLogMessage;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.service.ResourceService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional
public class AuditLogsListener {

    private AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private ResourceService resourceService;

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_AUDIT_LOGS_NAME, messageConverter = "jsonMessageConverter", concurrency = "${messaging.concurrency.audit-logs}")
    public void processMessage(final AuditLogMessage auditLogMessage) {

        LogRecord.LogRecordBuilder builder = LogRecord.builder()
                .audited(true)
                .version(auditLogMessage.getVersion())
                .message(auditLogMessage.getMessage())
                .actor(new ActorRecord(auditLogMessage.getActorType(), auditLogMessage.getActorAuthMethod(), auditLogMessage.getActorUuid(), auditLogMessage.getActorName()))
                .additionalData(auditLogMessage.getAdditionalData())
                .module(auditLogMessage.getModule())
                .source(new SourceRecord(auditLogMessage.getMethod(), auditLogMessage.getPath(), auditLogMessage.getContentType(), auditLogMessage.getIpAddress(), auditLogMessage.getUserAgent()))
                .operationData(auditLogMessage.getOperationData())
                .operation(auditLogMessage.getOperation())
                .operationResult(auditLogMessage.getOperationResult())
                .resource(new ResourceRecord(auditLogMessage.getResource(), enrichNamesAndUuids(auditLogMessage.getResourceNamesAndUuids(), auditLogMessage.getResource())))
                .affiliatedResource(new ResourceRecord(auditLogMessage.getAffiliatedResource(), enrichNamesAndUuids(auditLogMessage.getAffiliatedResourceNamesAndUuids(), auditLogMessage.getAffiliatedResource())));
        LogRecord logRecord = builder.build();
        auditLogService.log(logRecord);
    }

    private List<NameAndUuid> enrichNamesAndUuids(List<NameAndUuid> resourceNamesAndUuids, Resource resource) {
        if (resourceNamesAndUuids != null) {
            for (NameAndUuid nameAndUuid : resourceNamesAndUuids) {
                if (nameAndUuid.getUuid() != null && nameAndUuid.getName() == null) {
                    try {
                        nameAndUuid.setName(resourceService.getResourceObject(resource, nameAndUuid.getUuid()).getName());
                    } catch (NotFoundException ignored) {
                        // Did not manage to retrieve object name
                    }
                }
            }
        }
        return resourceNamesAndUuids;
    }


}
