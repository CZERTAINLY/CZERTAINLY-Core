package com.czertainly.core.service;

import com.czertainly.api.interfaces.core.web.AuditLogController;
import com.czertainly.api.interfaces.core.web.SettingController;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.audit.ExportResultDto;
import com.czertainly.api.model.core.logging.enums.AuditLogOutput;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.settings.logging.AuditLoggingSettingsDto;
import com.czertainly.api.model.core.settings.logging.LoggingSettingsDto;
import com.czertainly.api.model.core.settings.logging.ResourceLoggingSettingsDto;
import com.czertainly.core.dao.repository.AuditLogRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

@SpringBootTest
class AuditLogServiceTest extends BaseSpringBootTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SettingController settingController;

    @Autowired
    private SettingService settingService;

    @Autowired
    private AuditLogController auditLogController;

    @BeforeEach
    public void setUp() {
        LoggingSettingsDto loggingSettingsDto = new LoggingSettingsDto();

        AuditLoggingSettingsDto auditLoggingSettingsDto = new AuditLoggingSettingsDto();
        auditLoggingSettingsDto.setOutput(AuditLogOutput.ALL);
        auditLoggingSettingsDto.setLogAllModules(true);
        auditLoggingSettingsDto.setLogAllResources(true);
        loggingSettingsDto.setAuditLogs(auditLoggingSettingsDto);

        ResourceLoggingSettingsDto eventLoggingSettingsDto = new ResourceLoggingSettingsDto();
        eventLoggingSettingsDto.setLogAllModules(true);
        eventLoggingSettingsDto.setLogAllResources(true);
        loggingSettingsDto.setEventLogs(eventLoggingSettingsDto);

        settingService.updateLoggingSettings(loggingSettingsDto);
    }

    @Test
    void testExportAuditLog() {
        auditLogController.listAuditLogs(new SearchRequestDto());
        ExportResultDto result = auditLogService.exportAuditLogs(List.of());

        Assertions.assertDoesNotThrow(() -> {
            try (FileOutputStream fos = new FileOutputStream(File.createTempFile(result.getFileName(), ""))) {
                fos.write(result.getFileContent());
                fos.flush();
            }
        });
    }

    @Test
    void testPurgeAuditLogs() {

        auditLogController.listAuditLogs(new SearchRequestDto());
        auditLogController.listAuditLogs(new SearchRequestDto());
        auditLogController.listAuditLogs(new SearchRequestDto());
        settingController.getLoggingSettings();
        settingController.getPlatformSettings();

        Assertions.assertEquals(5, auditLogRepository.findAll().size());

        SearchFilterRequestDto searchFilter = new SearchFilterRequestDto();
        searchFilter.setFieldSource(FilterFieldSource.PROPERTY);
        searchFilter.setCondition(FilterConditionOperator.EQUALS);
        searchFilter.setFieldIdentifier(FilterField.AUDIT_LOG_RESOURCE.toString());
        searchFilter.setValue(Resource.SETTINGS.getCode());
        auditLogService.purgeAuditLogs(List.of(searchFilter));

        Assertions.assertEquals(3, auditLogRepository.findAll().size());

        auditLogService.purgeAuditLogs(List.of());

        // we expect 0 record to be available after purging all because only controller is annotated to do audit logging
        Assertions.assertEquals(0, auditLogRepository.findAll().size());
    }
}
