package com.czertainly.core.aop;

import com.czertainly.api.model.common.Named;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.core.dao.entity.AuditLog;
import com.czertainly.core.dao.repository.AuditLogRepository;
import com.czertainly.core.logging.AuditLogOutput;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.logging.LoggerWrapper;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.api.model.core.logging.Loggable;
import com.czertainly.api.model.core.logging.records.LogRecord;
import com.czertainly.api.model.core.logging.records.ResourceRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.Serializable;
import java.lang.reflect.Parameter;
import java.util.*;

@Aspect
@Component
@ConditionalOnExpression("${auditlog.enabled:true}")
public class AuditLogAspect {

    private static final LoggerWrapper logger = new LoggerWrapper(AuditLogAspect.class, null, null);

    private AuditLogRepository auditLogRepository;

    @Value("${logging.schema-version}")
    private String schemaVersion;

    @Value("${auditlog.output}")
    private AuditLogOutput auditLogOutput;

    @Autowired
    public void setAuditLogRepository(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Around("@annotation(AuditLogged)")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        // if in non-request context, do not log
        if (RequestContextHolder.getRequestAttributes() == null) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AuditLogged annotation = signature.getMethod().getAnnotation(AuditLogged.class);

        LogRecord.LogRecordBuilder logBuilder = LogRecord.builder()
                .version(schemaVersion) // hardcoded for now
                .audited(true)
                .module(annotation.module())
                .actor(LoggingHelper.getActorInfo())
                .source(LoggingHelper.getSourceInfo());

        Object result = null;
        try {
            result = joinPoint.proceed();
            logBuilder.operationResult(OperationResult.SUCCESS);

            return result;
        } catch (Exception e) {
            logBuilder.operationResult(OperationResult.FAILURE);
            logBuilder.message(e.getMessage());
            throw e;
        } finally {
            constructLogData(annotation, logBuilder, signature.getMethod().getParameters(), joinPoint.getArgs(), result);

            LogRecord logRecord = logBuilder.build();

            if (auditLogOutput == AuditLogOutput.ALL || auditLogOutput == AuditLogOutput.DATABASE) {
                AuditLog auditLog = AuditLog.fromLogRecord(logRecord);
                auditLogRepository.save(auditLog);
            }

            if (auditLogOutput == AuditLogOutput.ALL || auditLogOutput == AuditLogOutput.CONSOLE) {
                logger.logAudited(logRecord);
            }
        }
    }

    private void constructLogData(AuditLogged annotation, LogRecord.LogRecordBuilder logBuilder, Parameter[] parameters, Object[] parameterValues, Object response) {
        String resourceName = null;
        List<UUID> resourceUuids = null;
        String affiliatedResourceName = null;
        List<UUID> affiliatedResourceUuids = null;
        Serializable responseOperationData = null;
        Map<String, Object> data = new LinkedHashMap<>();

        if (parameters != null && parameterValues != null) {
            for (int i = 0; i < parameters.length; i++) {
                String parameterName = parameters[i].getName();
                Object parameterValue = parameterValues[i];

                if (resourceUuids == null || resourceName == null || (annotation.affiliatedResource() != Resource.NONE && (affiliatedResourceUuids == null || affiliatedResourceName == null))) {
                    LogResource logResource = parameters[i].getAnnotation(LogResource.class);
                    List<UUID> paramResourceUuids = getResourceUuidsFromParameter(logResource, parameterName, parameterValue);
                    String paramResourceName = getResourceNameFromParameter(logResource, parameterName, parameterValue);

                    boolean isAffiliated = logResource != null && logResource.affiliated();
                    if (paramResourceUuids != null) {
                        if (isAffiliated) {
                            affiliatedResourceUuids = paramResourceUuids;
                        } else {
                            resourceUuids = paramResourceUuids;
                        }
                    }
                    if (paramResourceName != null) {
                        if (isAffiliated) {
                            affiliatedResourceName = paramResourceName;
                        } else {
                            resourceName = paramResourceName;
                        }
                    }
                }

                if (logger.getLogger().isDebugEnabled()) {
                    if (parameterValue instanceof Optional<?> optional) {
                        parameterValue = optional.orElse(null);
                    }
                    data.put(parameterName, parameterValue);
                }
            }
        }

        if (response != null) {
            if (response instanceof ResponseEntity<?> responseEntity) {
                response = responseEntity.getBody();
            }

            if (response instanceof Loggable loggable) {
                responseOperationData = loggable.toLogData();
            }
        }

        Operation operation = annotation.operation() != Operation.UNKNOWN ? annotation.operation() : LoggingHelper.getAuditLogOperation();
        logBuilder.operation(operation);
        logBuilder.resource(constructResourceRecord(false, annotation.resource(), resourceUuids, annotation.name().isEmpty() ? resourceName : annotation.name()));
        if (annotation.affiliatedResource() != Resource.NONE) {
            logBuilder.affiliatedResource(constructResourceRecord(true, annotation.affiliatedResource(), affiliatedResourceUuids, affiliatedResourceName));
        }
        logBuilder.operationData(responseOperationData);
        if (!data.isEmpty()) {
            logBuilder.additionalData(data);
        }
    }

    private List<UUID> getResourceUuidsFromParameter(LogResource logResource, String parameterName, Object parameterValue) {
        if (logResource != null) {
            if (logResource.uuid()) {
                return parameterValue instanceof List<?> listValues
                        ? listValues.stream().map(v -> UUID.fromString(v.toString())).toList()
                        : List.of(UUID.fromString(parameterValue.toString()));
            }
            return null;
        }
        if (parameterName.equalsIgnoreCase("uuid")) {
            if (parameterValue instanceof String paramString) {
                return List.of(UUID.fromString(paramString));
            }
            if (parameterValue instanceof UUID paramUuid) {
                return List.of(paramUuid);
            }
        }

        return null;
    }

    private String getResourceNameFromParameter(LogResource logResource, String parameterName, Object parameterValue) {
        if (logResource != null) {
            if (logResource.name()) {
                return parameterValue instanceof IPlatformEnum platformEnum ? platformEnum.getCode() : parameterValue.toString();
            }
            return null;
        }
        if (parameterValue instanceof Named named) {
            return named.getName();
        }
        return null;
    }

    private ResourceRecord constructResourceRecord(boolean affiliated, Resource resource, List<UUID> resourceUuids, String resourceName) {
        List<String> resourceNames = resourceName == null ? null : List.of(resourceName);
        if (resourceUuids == null || resourceNames == null) {
            ResourceRecord storedResource = LoggingHelper.getLogResourceInfo(affiliated);
            if (storedResource != null && storedResource.type() == resource) {
                if (resourceUuids == null) {
                    resourceUuids = storedResource.uuids();
                }
                if (resourceNames == null) {
                    resourceNames = storedResource.names();
                }
            }
        }
        return new ResourceRecord(resource, resourceUuids, resourceNames);
    }

}
