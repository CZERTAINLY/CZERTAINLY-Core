package com.czertainly.core.service;

import com.czertainly.api.interfaces.core.web.AuditLogController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.audit.AuditLogResponseDto;
import com.czertainly.api.model.core.audit.ExportResultDto;
import com.czertainly.core.dao.repository.AuditLogRepository;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

@SpringBootTest
public class AuditLogServiceTest extends BaseSpringBootTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogController auditLogController;

    @Test
    void testExportAuditLog() throws IOException {
        auditLogController.listAuditLogs(new SearchRequestDto());
        ExportResultDto result = auditLogService.exportAuditLogs(List.of());

        try (FileOutputStream fos = new FileOutputStream(File.createTempFile(result.getFileName(), ""))) {
            fos.write(result.getFileContent());
            fos.flush();
        }
    }

    @Test
    void testPurgeAuditLogs() {
        auditLogController.listAuditLogs(new SearchRequestDto());
        auditLogController.listAuditLogs(new SearchRequestDto());
        auditLogController.listAuditLogs(new SearchRequestDto());

        Assertions.assertEquals(3, auditLogRepository.findAll().size());

        auditLogService.purgeAuditLogs(List.of());

        // we expect 0 record to be available after purging all because only controller is annotated to do audit logging
        Assertions.assertEquals(0, auditLogRepository.findAll().size());
    }
}
