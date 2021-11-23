package com.czertainly.core;

import com.czertainly.core.service.AuditLogService;
import com.czertainly.api.core.modal.AuditLogFilter;
import com.czertainly.api.core.modal.ExportResultDto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SpringBootTest
public class AuditLogExportTest {

    @Autowired
    private AuditLogService auditLogService;

    @Test
    @WithMockUser(roles="SUPERADMINISTRATOR")
    public void testExportAuditLog() throws IOException {
        auditLogService.listAuditLogs(new AuditLogFilter(), Pageable.unpaged());
        ExportResultDto result = auditLogService.exportAuditLogs(new AuditLogFilter(), Sort.by("id"));

        try (FileOutputStream fos = new FileOutputStream(File.createTempFile(result.getFileName(), ""))) {
            fos.write(result.getFileContent());
            fos.flush();
        }
    }
}
