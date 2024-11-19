package com.czertainly.core.service;

import com.czertainly.api.model.core.audit.AuditLogFilter;
import com.czertainly.api.model.core.audit.AuditLogResponseDto;
import com.czertainly.api.model.core.audit.ExportResultDto;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SpringBootTest
public class AuditLogExportTest extends BaseSpringBootTest {

    @Autowired
    private AuditLogService auditLogService;

    @Test
    public void testExportAuditLog() throws IOException {
        auditLogService.listAuditLogs(new AuditLogFilter(), Pageable.ofSize(10));
        ExportResultDto result = auditLogService.exportAuditLogs(new AuditLogFilter(), Sort.by("id"));

        try (FileOutputStream fos = new FileOutputStream(File.createTempFile(result.getFileName(), ""))) {
            fos.write(result.getFileContent());
            fos.flush();
        }
    }

    @Test
    public void testPurgeAuditLogs() {
        auditLogService.logStartup();
        auditLogService.logShutdown();
        auditLogService.purgeAuditLogs(new AuditLogFilter(), Pageable.unpaged().getSort());
        AuditLogResponseDto logs = auditLogService.listAuditLogs(new AuditLogFilter(), Pageable.ofSize(10));

        // if the audit log is enabled, purging audit logs will keep the delete operation in the audit log
        // therefore we expect only 1 record to be available after purging all
        Assertions.assertEquals(1, logs.getItems().size());
    }
}
