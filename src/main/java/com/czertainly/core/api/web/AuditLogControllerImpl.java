package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.AuditLogController;
import com.czertainly.api.model.core.audit.AuditLogFilter;
import com.czertainly.api.model.core.audit.AuditLogResponseDto;
import com.czertainly.api.model.core.audit.ExportResultDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationStatusEnum;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class AuditLogControllerImpl implements AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @Override
    @AuthEndpoint(resourceName = Resource.AUDIT_LOG, actionName = ResourceAction.LIST, isListingEndPoint = true)
    public AuditLogResponseDto listAuditLogs(AuditLogFilter filter, Pageable pageable) {
        return auditLogService.listAuditLogs(filter, pageable);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.AUDIT_LOG, actionName = ResourceAction.EXPORT)
    public ResponseEntity<org.springframework.core.io.Resource> exportAuditLogs(AuditLogFilter filter, Pageable pageable) {
        ExportResultDto export = auditLogService.exportAuditLogs(filter, pageable.getSort());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(export.getFileContent().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + export.getFileName())
                .body(new ByteArrayResource(export.getFileContent()));
    }

    @Override
    public List<String> listObjects() {
        return Arrays.stream(ObjectType.values()).map(ObjectType::name).collect(Collectors.toList());
    }

    @Override
    public List<String> listOperations() {
        return Arrays.stream(OperationType.values()).map(OperationType::name).collect(Collectors.toList());
    }

    @Override
    public List<String> listOperationStatuses() {
        return Arrays.stream(OperationStatusEnum.values()).map(OperationStatusEnum::name).collect(Collectors.toList());
    }
}
