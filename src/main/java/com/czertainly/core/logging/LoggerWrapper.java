package com.czertainly.core.logging;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.logging.records.LogRecord;
import com.czertainly.api.model.core.logging.records.ResourceRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.Serializable;

@Getter
public class LoggerWrapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Logger logger;
    private final Module module;
    private final Resource resource;

    public Logger getLogger() {
        return logger;
    }

    // Constructor that wraps around SLF4J Logger
    public LoggerWrapper(Class<?> clazz, Module module, Resource resource) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.module = module;
        this.resource = resource;
    }

    public void logAudited(LogRecord logRecord) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info(OBJECT_MAPPER.writeValueAsString(logRecord));
            }
        } catch (JsonProcessingException e) {
            logger.warn("Cannot serialize LogRecord to JSON: {}", e.getMessage());
        }
    }

    public void logEvent(Operation operation, OperationResult operationResult, Serializable operationData, String message) {
        if(logger.isInfoEnabled()) {
            try {
                String logRecordBody = getLogRecordBody(operation, operationResult, operationData, message);
                logger.info(logRecordBody);
            } catch (JsonProcessingException e) {
                logger.warn("Cannot serialize event LogRecord to JSON: {}", e.getMessage());
            }
        }
    }

    public void logEventDebug(Operation operation, OperationResult operationResult, Serializable operationData, String message, Object... args) {
        if(logger.isDebugEnabled()) {
            try {
                String logRecordBody = getLogRecordBody(operation, operationResult, operationData, message);
                logger.info(logRecordBody);
            } catch (JsonProcessingException e) {
            }
        }
    }

    private String getLogRecordBody(Operation operation, OperationResult operationResult, Serializable operationData, String message) throws JsonProcessingException {
        var logBuilder = prepareLogRecord();
        LogRecord logRecord = logBuilder
                .operation(operation)
                .operationResult(operationResult)
                .operationData(operationData)
                .message(message)
                .build();

        return OBJECT_MAPPER.writeValueAsString(logRecord);
    }

    private LogRecord.LogRecordBuilder prepareLogRecord() {
        return LogRecord.builder()
                .version("1.0")
                .audited(false)
                .module(this.module)
                .actor(LoggingHelper.getActorInfo())
                .source(LoggingHelper.getSourceInfo())
                .resource(new ResourceRecord(this.resource, null, (String)null))
                .operation(Operation.LIST);
    }
}
