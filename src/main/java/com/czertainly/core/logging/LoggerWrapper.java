package com.czertainly.core.logging;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.logging.records.LogRecord;
import com.czertainly.api.model.core.logging.records.ResourceRecord;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.logging.LoggingSettingsDto;
import com.czertainly.api.model.core.settings.logging.ResourceLoggingSettingsDto;
import com.czertainly.core.settings.SettingsCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

@Getter
public class LoggerWrapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Logger logger;
    private final Module module;
    private final Resource resource;

    // Constructor that wraps around SLF4J Logger
    public LoggerWrapper(Class<?> clazz, Module module, Resource resource) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.module = module;
        this.resource = resource;
    }

    public void logAudited(LogRecord logRecord) {
        try {
            if (logger.isInfoEnabled()) {
                ResourceLoggingSettingsDto loggingSettings = ((LoggingSettingsDto) SettingsCache.getSettings(SettingsSection.LOGGING)).getAuditLogs();
                if (filterLog(loggingSettings, logRecord.module(), logRecord.resource().type())) {
                    return;
                }
                logger.info(OBJECT_MAPPER.writeValueAsString(logRecord));
            }
        } catch (JsonProcessingException e) {
            logger.warn("Cannot serialize LogRecord to JSON: {}", e.getMessage());
        }
    }

    public void logEvent(Operation operation, OperationResult operationResult, Serializable operationData, String message) {
        if ((operationResult == OperationResult.SUCCESS && !logger.isInfoEnabled()) || (operationResult == OperationResult.FAILURE && !logger.isErrorEnabled())) {
            return;
        }

        if (logger.isInfoEnabled()) {
            try {
                LogRecord logRecord = buildLogRecord(operation, operationResult, operationData, message);
                ResourceLoggingSettingsDto loggingSettings = ((LoggingSettingsDto) SettingsCache.getSettings(SettingsSection.LOGGING)).getEventLogs();
                if (filterLog(loggingSettings, logRecord.module(), logRecord.resource().type())) {
                    return;
                }

                logger.info(OBJECT_MAPPER.writeValueAsString(logRecord));
            } catch (JsonProcessingException e) {
                logger.warn("Cannot serialize event LogRecord to JSON: {}", e.getMessage());
            }
        }
    }

    public void logEventDebug(Operation operation, OperationResult operationResult, Serializable operationData, String message) {
        if (logger.isDebugEnabled()) {
            try {
                LogRecord logRecord = buildLogRecord(operation, operationResult, operationData, message);
                ResourceLoggingSettingsDto loggingSettings = ((LoggingSettingsDto) SettingsCache.getSettings(SettingsSection.LOGGING)).getEventLogs();
                if (filterLog(loggingSettings, logRecord.module(), logRecord.resource().type())) {
                    return;
                }

                logger.debug(OBJECT_MAPPER.writeValueAsString(logRecord));
            } catch (JsonProcessingException e) {
                logger.warn("Cannot serialize debug event LogRecord to JSON: {}", e.getMessage());
            }
        }
    }

    public boolean filterLog(ResourceLoggingSettingsDto settings, Module module, Resource resource) {
        if (settings.getIgnoredModules().contains(module) || (!settings.isLogAllModules() && !settings.getLoggedModules().contains(module))) {
            return false;
        }
        return settings.getIgnoredResources().contains(resource) || (!settings.isLogAllResources() && !settings.getLoggedResources().contains(resource));
    }

    private LogRecord buildLogRecord(Operation operation, OperationResult operationResult, Serializable operationData, String message) {
        var logBuilder = prepareLogRecord();
        return logBuilder
                .operation(operation)
                .operationResult(operationResult)
                .operationData(operationData)
                .message(message)
                .build();
    }

    private LogRecord.LogRecordBuilder prepareLogRecord() {
        return LogRecord.builder()
                .version("1.0")
                .audited(false)
                .module(this.module)
                .actor(LoggingHelper.getActorInfo())
                .source(LoggingHelper.getSourceInfo())
                .resource(new ResourceRecord(this.resource, null, (String) null));
    }
}
