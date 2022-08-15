package com.czertainly.core;

import com.czertainly.api.model.core.audit.AuditLogFilter;
import com.czertainly.api.model.core.audit.ExportResultDto;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.util.BaseSpringBootTest;
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
        auditLogService.listAuditLogs(new AuditLogFilter(), Pageable.unpaged());
        ExportResultDto result = auditLogService.exportAuditLogs(new AuditLogFilter(), Sort.by("id"));

        try (FileOutputStream fos = new FileOutputStream(File.createTempFile(result.getFileName(), ""))) {
            fos.write(result.getFileContent());
            fos.flush();
        }
    }
}
