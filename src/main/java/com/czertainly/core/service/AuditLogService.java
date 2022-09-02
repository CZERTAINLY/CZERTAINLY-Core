package com.czertainly.core.service;

import com.czertainly.api.model.core.audit.AuditLogFilter;
import com.czertainly.api.model.core.audit.AuditLogResponseDto;
import com.czertainly.api.model.core.audit.ExportResultDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationStatusEnum;
import com.czertainly.api.model.core.audit.OperationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;


public interface AuditLogService {

    /**
     *
     * @param origination
     * @param affected
     * @param objectIdentifier
     * @param operation
     * @param operationStatus
     * @param additionalData
     *
     */
    void log(ObjectType origination,
             ObjectType affected,
             String objectIdentifier,
             OperationType operation,
             OperationStatusEnum operationStatus,
             Map<Object, Object> additionalData);

    /**
     *
     */
    void logStartup();

    /**
     *
     */
    void logShutdown();

    /**
     *
     * @param filter {@link AuditLogFilter}
     * @param pageable {@link Pageable}
     *
     * @return {@link AuditLogResponseDto}
     */
    AuditLogResponseDto listAuditLogs(AuditLogFilter filter, Pageable pageable);

    /**
     *
     * @param filter {@link AuditLogFilter}
     * @param sort {@link Sort}
     *
     * @return {@link ExportResultDto}
     */
    ExportResultDto exportAuditLogs(AuditLogFilter filter, Sort sort);

    /**
     * Removes the audit logs from the database
     * @param filter {@link AuditLogFilter}
     * @param sort {@link Sort}
     */
    void purgeAuditLogs(AuditLogFilter filter, Sort sort);
}
