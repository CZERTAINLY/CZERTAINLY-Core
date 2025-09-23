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
import java.util.Map;

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
        if (!logger.isInfoEnabled() || isLogFiltered(true, logRecord.module(), logRecord.resource().type(), null)) {
            return;
        }

        try {
            logger.info(OBJECT_MAPPER.writeValueAsString(logRecord));
        } catch (JsonProcessingException e) {
            logger.warn("Cannot serialize audit LogRecord to JSON: {}", e.getMessage());
        }
    }

    public void logEvent(Operation operation, OperationResult operationResult, Serializable operationData, String message) {
        if (isLogFiltered(false, this.module, this.resource, operationResult)) {
            return;
        }

        try {
            LogRecord logRecord = buildLogRecord(false, this.module, this.resource, operation, operationResult, operationData, message, null);
            if (operationResult == OperationResult.SUCCESS) {
                logger.info(OBJECT_MAPPER.writeValueAsString(logRecord));
            } else {
                logger.error(OBJECT_MAPPER.writeValueAsString(logRecord));
            }


        } catch (JsonProcessingException e) {
            logger.warn("Cannot serialize event LogRecord to JSON: {}", e.getMessage());
        }
    }

    public void logEventDebug(Operation operation, OperationResult operationResult, Serializable operationData, String message) {
        if (!logger.isDebugEnabled() || isLogFiltered(false, this.module, this.resource, operationResult)) {
            return;
        }

        try {
            LogRecord logRecord = buildLogRecord(false, this.module, this.resource, operation, operationResult, operationData, message, null);
            logger.debug(OBJECT_MAPPER.writeValueAsString(logRecord));
        } catch (JsonProcessingException e) {
            logger.warn("Cannot serialize debug event LogRecord to JSON: {}", e.getMessage());
        }
    }

    public boolean isLogFiltered(boolean audited, Module module, Resource resource, OperationResult result) {
        if (result != null && ((result == OperationResult.SUCCESS && !logger.isInfoEnabled()) || (result == OperationResult.FAILURE && !logger.isErrorEnabled()))) {
            return true;
        }

        ResourceLoggingSettingsDto settings = getLoggingSettings(audited);
        if (settings.getIgnoredModules().contains(module) || (!settings.isLogAllModules() && !settings.getLoggedModules().contains(module))) {
            return true;
        }
        return settings.getIgnoredResources().contains(resource) || (!settings.isLogAllResources() && !settings.getLoggedResources().contains(resource));
    }

    public LogRecord buildLogRecord(boolean audited, Module module, Resource resource, Operation operation, OperationResult operationResult, Serializable operationData, String message, Map<String, Object> additionalData) {
        if (module == null) module = this.module;
        if (resource == null) resource = this.resource;

        var logBuilder = prepareLogRecord(audited, module, resource);
        return logBuilder
                .operation(operation)
                .operationResult(operationResult)
                .operationData(operationData)
                .message(message)
                .additionalData(additionalData)
                .build();
    }

    private LogRecord.LogRecordBuilder prepareLogRecord(boolean audited, Module module, Resource resource) {
        return LogRecord.builder()
                .version("1.1")
                .audited(audited)
                .module(module)
                .actor(LoggingHelper.getActorInfo())
                .source(LoggingHelper.getSourceInfo())
                .resource(new ResourceRecord(resource, null));
    }

    private ResourceLoggingSettingsDto getLoggingSettings(boolean audited) {
        LoggingSettingsDto loggingSettings = SettingsCache.getSettings(SettingsSection.LOGGING);
        if (loggingSettings == null) {
            return new ResourceLoggingSettingsDto();
        }
        return audited ? loggingSettings.getAuditLogs() : loggingSettings.getEventLogs();
    }
}
