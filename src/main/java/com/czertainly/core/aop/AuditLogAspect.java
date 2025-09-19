package com.czertainly.core.aop;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.Named;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.Sensitive;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.logging.records.*;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.logging.LoggingSettingsDto;
import com.czertainly.api.model.core.logging.enums.AuditLogOutput;
import com.czertainly.core.logging.AuditLogEnhancer;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.api.model.core.logging.Loggable;
import com.czertainly.core.messaging.model.AuditLogMessage;
import com.czertainly.core.messaging.producers.AuditLogsProducer;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.BeautificationUtil;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Value("${logging.schema-version}")
    private String schemaVersion;

    private AuditLogsProducer auditLogsProducer;

    private AuditLogEnhancer auditLogEnhancer;

    @Autowired
    public void setAuditLogEnhancer(AuditLogEnhancer auditLogEnhancer) {
        this.auditLogEnhancer = auditLogEnhancer;
    }

    @Autowired
    public void setAuditLogsProducer(AuditLogsProducer auditLogsProducer) {
        this.auditLogsProducer = auditLogsProducer;
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
            constructLogData(annotation, logBuilder, signature.getMethod().getParameters(), joinPoint.getArgs(), result, loggingSettingsDto.getAuditLogs().isVerbose());
            auditLogsProducer.produceMessage(new AuditLogMessage(logBuilder.build()));
        }
    }

    private void constructLogData(AuditLogged annotation, LogRecord.LogRecordBuilder logBuilder, Parameter[] parameters, Object[] parameterValues, Object response, boolean verbose) {
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

                if (verbose && !parameters[i].isAnnotationPresent(Sensitive.class)) {
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
                // Add data from response
                if (responseOperationData instanceof NameAndUuidDto nameAndUuidDto) {
                    if (resourceName == null) resourceName = nameAndUuidDto.getName();
                    if (nameAndUuidDto.getUuid() != null) {
                        if (resourceUuids == null) resourceUuids = new ArrayList<>();
                        resourceUuids.add(UUID.fromString(nameAndUuidDto.getUuid()));
                    }
                }
            }
        }

        Operation operation = annotation.operation() != Operation.UNKNOWN ? annotation.operation() : LoggingHelper.getAuditLogOperation();
        logBuilder.operation(operation);
        logBuilder.resource(constructResourceRecord(false, resource, resourceUuids, annotation.name().isEmpty() ? resourceName : annotation.name(), operation));
        if (affiliatedResource != Resource.NONE) {
            logBuilder.affiliatedResource(constructResourceRecord(true, affiliatedResource, affiliatedResourceUuids, affiliatedResourceName, operation));
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
                        : (parameterValue instanceof Optional<?> optional && optional.isPresent() ? new ArrayList<>(List.of(UUID.fromString(optional.get().toString()))) : new ArrayList<>(List.of(UUID.fromString(parameterValue.toString()))));
            }
            return null;
        }
        if (parameterName.equalsIgnoreCase("uuid")) {
            if (parameterValue instanceof String paramString) {
                return new ArrayList<>(List.of(UUID.fromString(paramString)));
            }
            if (parameterValue instanceof UUID paramUuid) {
                return new ArrayList<>(List.of(paramUuid));
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

    private ResourceRecord constructResourceRecord(boolean affiliated, Resource resource, List<UUID> resourceUuids, String resourceName, Operation operation) {
        List<NameAndUuid> objects = new ArrayList<>();

        // step 1: first uuid with provided resourceName (if available)
        if (resourceUuids != null && !resourceUuids.isEmpty() && resourceName != null) {
            objects.add(new NameAndUuid(resourceName, resourceUuids.getFirst()));
        } else if (resourceUuids == null && resourceName != null) objects.add(new NameAndUuid(resourceName, null));


        // prepare lookup from stored resource if needed
        Map<UUID, String> storedUuidToName = new HashMap<>();
        ResourceRecord storedResource = null;
        if (resourceUuids == null || resourceName == null) {
            storedResource = LoggingHelper.getLogResourceInfo(affiliated);
            if (storedResource != null && storedResource.type() == resource) {
                for (NameAndUuid storedObject : storedResource.objects()) {
                    storedUuidToName.put(storedObject.uuid(), storedObject.name());
                }
            }
        }

        // step 2: remaining uuids (use stored name if available, otherwise null)
        if (resourceUuids != null && !resourceUuids.isEmpty()) {
            for (UUID uuid : resourceUuids) {
                String name = storedUuidToName.getOrDefault(uuid, null);
                objects.add(new NameAndUuid(name, uuid));
            }
        }

        // step 3: add all stored objects not already in resourceUuids
        if (storedResource != null) {
            for (NameAndUuid storedObject : storedResource.objects()) {
                if (resourceUuids == null || !resourceUuids.contains(storedObject.uuid())) {
                    objects.add(storedObject);
                }
            }
        }

        // if operation is delete, also call resource service
        if (operation == Operation.DELETE || operation == Operation.FORCE_DELETE)
            objects = auditLogEnhancer.enrichNamesAndUuids(objects, resource);

        return new ResourceRecord(resource, objects);
    }

}
