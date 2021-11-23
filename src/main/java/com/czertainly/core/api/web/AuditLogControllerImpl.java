package com.czertainly.core.api.web;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.czertainly.core.service.AuditLogService;
import com.czertainly.api.core.interfaces.web.AuditLogController;
import com.czertainly.api.core.modal.AuditLogFilter;
import com.czertainly.api.core.modal.AuditLogResponseDto;
import com.czertainly.api.core.modal.ExportResultDto;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationStatusEnum;
import com.czertainly.api.core.modal.OperationType;

@RestController
public class AuditLogControllerImpl implements AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @Override
    public AuditLogResponseDto listAuditLogs(AuditLogFilter filter, Pageable pageable) {
        return auditLogService.listAuditLogs(filter, pageable);
    }

    @Override
    public ResponseEntity<Resource> exportAuditLogs(AuditLogFilter filter, Pageable pageable) {
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
