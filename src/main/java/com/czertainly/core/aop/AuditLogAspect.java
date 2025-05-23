package com.czertainly.core.aop;

import com.czertainly.api.model.common.Named;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.Sensitive;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.logging.LoggingSettingsDto;
import com.czertainly.api.model.core.logging.enums.AuditLogOutput;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.logging.LoggerWrapper;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.api.model.core.logging.Loggable;
import com.czertainly.api.model.core.logging.records.LogRecord;
import com.czertainly.api.model.core.logging.records.ResourceRecord;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.BeautificationUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.lang.reflect.Parameter;
import java.util.*;

@Aspect
@Component
public class AuditLogAspect {

    private static final LoggerWrapper logger = new LoggerWrapper(AuditLogAspect.class, null, null);

    @Value("${logging.schema-version}")
    private String schemaVersion;

    private AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(AuditLogged)")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        LoggingSettingsDto loggingSettingsDto = SettingsCache.getSettings(SettingsSection.LOGGING);
        if (loggingSettingsDto == null || (loggingSettingsDto.getAuditLogs().getOutput() == AuditLogOutput.NONE)) {
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
            String message = e.getMessage();
            if (e instanceof AccessDeniedException) {
                String resourceName = AuthHelper.getDeniedPermissionResource();
                String resourceActionName = AuthHelper.getDeniedPermissionResourceAction();
                message = "%s. Required '%s' action permission for resource '%s'".formatted(message, BeautificationUtil.camelToHumanForm(resourceActionName), Resource.findByCode(resourceName).getLabel());
            }

            logBuilder.operationResult(OperationResult.FAILURE);
            logBuilder.message(message);
            throw e;
        } finally {
            constructLogData(annotation, logBuilder, signature.getMethod().getParameters(), joinPoint.getArgs(), result);

            LogRecord logRecord = logBuilder.build();
            auditLogService.log(logRecord);
        }
    }

    private void constructLogData(AuditLogged annotation, LogRecord.LogRecordBuilder logBuilder, Parameter[] parameters, Object[] parameterValues, Object response) {
        Resource resource = null;
        String resourceName = null;
        List<UUID> resourceUuids = null;
        Resource affiliatedResource = null;
        String affiliatedResourceName = null;
        List<UUID> affiliatedResourceUuids = null;
        Serializable responseOperationData = null;
        Map<String, Object> data = new LinkedHashMap<>();

        if (parameters != null && parameterValues != null) {
            for (int i = 0; i < parameters.length; i++) {
                String parameterName = parameters[i].getName();
                Object parameterValue = parameterValues[i];

                LogResource logResource = parameters[i].getAnnotation(LogResource.class);
                List<UUID> paramResourceUuids = getResourceUuidsFromParameter(logResource, parameterName, parameterValue);
                String paramResourceName = getResourceNameFromParameter(logResource, parameterValue);
                Resource paramResource = getResourceFromParameter(logResource, parameterValue);

                boolean isAffiliated = logResource != null && logResource.affiliated();
                if (isAffiliated) {
                    if (paramResourceUuids != null) affiliatedResourceUuids = paramResourceUuids;
                    if (paramResourceName != null) affiliatedResourceName = paramResourceName;
                    if (paramResource != null) affiliatedResource = paramResource;
                } else {
                    if (paramResourceUuids != null) resourceUuids = paramResourceUuids;
                    if (paramResourceName != null) resourceName = paramResourceName;
                    if (paramResource != null) resource = paramResource;
                }

                if (logger.getLogger().isDebugEnabled() && !parameters[i].isAnnotationPresent(Sensitive.class)) {
                    if (parameterValue instanceof Optional<?> optional) {
                        parameterValue = optional.orElse(null);
                    }
                    data.put(parameterName, parameterValue);
                }
            }
        }

        if (resource == null) resource = annotation.resource();
        if (affiliatedResource == null) affiliatedResource = annotation.affiliatedResource();

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
        logBuilder.resource(constructResourceRecord(false, resource, resourceUuids, annotation.name().isEmpty() ? resourceName : annotation.name()));
        if (affiliatedResource != Resource.NONE) {
            logBuilder.affiliatedResource(constructResourceRecord(true, affiliatedResource, affiliatedResourceUuids, affiliatedResourceName));
        }
        logBuilder.operationData(responseOperationData);
        if (!data.isEmpty()) {
            logBuilder.additionalData(data);
        }
    }

    private Resource getResourceFromParameter(LogResource logResource, Object parameterValue) {
        return logResource != null && logResource.resource() && parameterValue instanceof Resource resource ? resource : null;
    }

    private List<UUID> getResourceUuidsFromParameter(LogResource logResource, String parameterName, Object parameterValue) {
        if (logResource != null) {
            if (logResource.uuid()) {
                if (parameterValue instanceof Optional<?> optional) {
                    if (optional.isEmpty()) return null;
                    parameterValue = optional.get();
                }

                return parameterValue instanceof List<?> listValues
                        ? listValues.stream().map(v -> UUID.fromString(v.toString())).toList()
                        : (parameterValue instanceof Optional<?> optional && optional.isPresent() ? List.of(UUID.fromString(optional.get().toString())) : List.of(UUID.fromString(parameterValue.toString())));
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

    private String getResourceNameFromParameter(LogResource logResource, Object parameterValue) {
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
