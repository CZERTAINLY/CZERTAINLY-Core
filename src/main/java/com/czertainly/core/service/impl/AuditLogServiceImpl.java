package com.czertainly.core.service.impl;

import com.czertainly.api.model.core.audit.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.AuditLog;
import com.czertainly.core.dao.entity.QAuditLog;
import com.czertainly.core.dao.repository.AuditLogRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.service.AuditLogService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditLogServiceImpl implements AuditLogService {

    private static final String LOGGER_NAME = "audit-log";
    private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.findAndRegisterModules();
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Value("${export.auditLog.fileName.prefix:audit-logs}")
    private String fileNamePrefix;

    @Value("${auditLog.enabled:false}")
    private boolean auditLogEnabled;

    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private ExportProcessor exportProcessor;

    @Override
    public void log(ObjectType origination,
                    ObjectType affected,
                    String objectIdentifier,
                    OperationType operation,
                    OperationStatusEnum operationStatus,
                    Map<Object, Object> additionalData
    ) {
        String additionalDataJson = null;
        try {
            MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            additionalDataJson = additionalData != null ? MAPPER.writeValueAsString(additionalData) : null;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setOrigination(origination);
        auditLog.setAffected(affected);
        auditLog.setObjectIdentifier(objectIdentifier);
        auditLog.setOperation(operation);
        auditLog.setOperationStatus(operationStatus);
        auditLog.setAdditionalData(additionalDataJson);

        auditLog = auditLogRepository.save(auditLog);

        try {
            logger.info(MAPPER.writeValueAsString(auditLog.mapToDto()));
        } catch (JsonProcessingException e) {
            logger.info(auditLog.mapToDto().toString());
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    @PostConstruct
    public void logStartup() {
        if (auditLogEnabled) {
            Map<Object, Object> additionalData = new HashMap<>();
            additionalData.put("message", "CZERTAINLY backend started");
            log(ObjectType.BE, ObjectType.BE, null, OperationType.START, OperationStatusEnum.SUCCESS, additionalData);
        }
    }

    @Override
    @PreDestroy
    public void logShutdown() {
        if (auditLogEnabled) {
            Map<Object, Object> additionalData = new HashMap<>();
            additionalData.put("message", "CZERTAINLY backend shutdown");
            log(ObjectType.BE, ObjectType.BE, null, OperationType.STOP, OperationStatusEnum.SUCCESS, additionalData);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.AUDIT_LOG, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.AUDIT_LOG, action = ResourceAction.LIST)
    public AuditLogResponseDto listAuditLogs(AuditLogFilter filter, Pageable pageable) {

        AuditLogResponseDto response = new AuditLogResponseDto();
        response.setItems(new ArrayList<>());

        if (auditLogRepository.count() <= 0) {
            return response;
        }

        Predicate predicate = createPredicate(filter);
        Page<AuditLog> result = auditLogRepository.findAll(predicate, pageable);
        long count = auditLogRepository.count(predicate);

        response.setItemsPerPage(pageable.getPageSize());
        response.setPageNumber(pageable.getPageNumber());
        response.setTotalItems(count);
        response.setTotalPages((int) Math.ceil((double) count / pageable.getPageSize()));

        if (result.getSize() > 0) {
            response.setItems(result.get().map(AuditLog::mapToDto).collect(Collectors.toList()));
        }

        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.AUDIT_LOG, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.AUDIT_LOG, action = ResourceAction.EXPORT)
    public ExportResultDto exportAuditLogs(AuditLogFilter filter, Sort sort) {
        Predicate predicate = createPredicate(filter);
        List<AuditLog> entities = auditLogRepository.findAll(predicate, sort);
        List<AuditLogDto> dtos = entities.stream().map(AuditLog::mapToDto).collect(Collectors.toList());

        return exportProcessor.generateExport(fileNamePrefix, dtos);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.AUDIT_LOG, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.AUDIT_LOG, action = ResourceAction.DELETE)
    public void purgeAuditLogs(AuditLogFilter filter, Sort sort) {
        Predicate predicate = createPredicate(filter);
        List<AuditLog> entities = auditLogRepository.findAll(predicate, sort);
        auditLogRepository.deleteAll(entities);
    }

    private Predicate createPredicate(AuditLogFilter filter) {
        BooleanBuilder predicate = new BooleanBuilder();
        ZoneId zoneId = ZoneId.systemDefault();


        if (StringUtils.isNotBlank(filter.getAuthor())) {
            predicate.and(QAuditLog.auditLog.author.likeIgnoreCase(filter.getAuthor()));
        }

        if (filter.getCreatedFrom() != null) {
            predicate.and(QAuditLog.auditLog.created.after(filter.getCreatedFrom().atStartOfDay().atZone(zoneId).toOffsetDateTime()));
        }
        if (filter.getCreatedTo() != null) {
            predicate.and(QAuditLog.auditLog.created.before(filter.getCreatedTo().atTime(LocalTime.MAX).atZone(zoneId).toOffsetDateTime()));
        } else {
            predicate.and(QAuditLog.auditLog.created.isNotNull());
        }

        if (filter.getOperation() != null) {
            predicate.and(QAuditLog.auditLog.operation.eq(filter.getOperation()));
        }
        if (filter.getOperationStatus() != null) {
            predicate.and(QAuditLog.auditLog.operationStatus.eq(filter.getOperationStatus()));
        }

        if (filter.getAffected() != null) {
            predicate.and(QAuditLog.auditLog.affected.eq(filter.getAffected()));
        }

        if (filter.getOrigination() != null) {
            predicate.and(QAuditLog.auditLog.origination.eq(filter.getOrigination()));
        }

        if (filter.getAffected() != null) {
            predicate.and(QAuditLog.auditLog.affected.eq(filter.getAffected()));
        }

        if (StringUtils.isNotBlank(filter.getObjectIdentifier())) {
            predicate.and(QAuditLog.auditLog.objectIdentifier.likeIgnoreCase(filter.getObjectIdentifier()));
        }

        return predicate;
    }
}
