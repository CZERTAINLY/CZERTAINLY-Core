package com.czertainly.core.aop;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.interfaces.core.web.CryptographicKeyController;
import com.czertainly.api.interfaces.core.web.SettingController;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.AuditLogOutput;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.logging.AuditLoggingSettingsDto;
import com.czertainly.api.model.core.settings.logging.LoggingSettingsDto;
import com.czertainly.api.model.core.settings.logging.ResourceLoggingSettingsDto;
import com.czertainly.core.dao.entity.AuditLog;
import com.czertainly.core.dao.repository.AuditLogRepository;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class AuditLogAspectTest extends BaseSpringBootTest {

    @Autowired
    private SettingService settingService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SettingController settingController;

    @Autowired
    private CryptographicKeyController keyController;

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
    void testListKeyPairsAudit() throws ConnectorException {
        keyController.listKeyPairs(Optional.empty());
        keyController.listKeyPairs(Optional.ofNullable(UUID.randomUUID().toString()));
        keyController.deleteKeys(List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        settingController.getLoggingSettings();

        var auditLogs = auditLogRepository.findAll();
        Assertions.assertEquals(4, auditLogs.size());

        AuditLog auditLogNoUuidResource = auditLogs.getFirst();
        Assertions.assertEquals(Resource.TOKEN_PROFILE, auditLogNoUuidResource.getLogRecord().affiliatedResource().type());
        Assertions.assertNull(auditLogNoUuidResource.getLogRecord().affiliatedResource().uuids());

        AuditLog auditLogWithUuidResource = auditLogs.get(1);
        Assertions.assertEquals(Resource.TOKEN_PROFILE, auditLogWithUuidResource.getLogRecord().affiliatedResource().type());
        Assertions.assertEquals(1, auditLogWithUuidResource.getLogRecord().affiliatedResource().uuids().size());

        AuditLog auditLogWithMoreUuidResource = auditLogs.get(2);
        Assertions.assertEquals(Resource.CRYPTOGRAPHIC_KEY, auditLogWithMoreUuidResource.getLogRecord().resource().type());
        Assertions.assertEquals(2, auditLogWithMoreUuidResource.getLogRecord().resource().uuids().size());

        AuditLog auditLogWithNamedResource = auditLogs.get(3);
        Assertions.assertEquals(Resource.SETTINGS, auditLogWithNamedResource.getLogRecord().resource().type());
        Assertions.assertEquals(SettingsSection.LOGGING.getCode(), auditLogWithNamedResource.getLogRecord().resource().names().getFirst());
    }

}
