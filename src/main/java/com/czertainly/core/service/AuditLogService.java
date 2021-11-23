package com.czertainly.core.service;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.czertainly.api.core.modal.AuditLogFilter;
import com.czertainly.api.core.modal.AuditLogResponseDto;
import com.czertainly.api.core.modal.ExportResultDto;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationStatusEnum;
import com.czertainly.api.core.modal.OperationType;


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
}
