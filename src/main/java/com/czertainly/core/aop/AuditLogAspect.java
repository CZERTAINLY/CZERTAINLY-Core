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
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.logging.LoggerWrapper;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.api.model.core.logging.Loggable;
import com.czertainly.core.messaging.model.AuditLogMessage;
import com.czertainly.core.messaging.producers.AuditLogsProducer;
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

    private AuditLogsProducer auditLogsProducer;

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

        AuditLogMessage auditLogMessage = new AuditLogMessage();

        auditLogMessage.setVersion(schemaVersion);
        auditLogMessage.setModule(annotation.module());
        ActorRecord actorRecord = LoggingHelper.getActorInfo();
        auditLogMessage.setActorAuthMethod(actorRecord.authMethod());
        auditLogMessage.setActorUuid(actorRecord.uuid());
        auditLogMessage.setActorType(actorRecord.type());
        auditLogMessage.setActorName(actorRecord.name());
        SourceRecord sourceRecord = LoggingHelper.getSourceInfo();
        if (sourceRecord != null) {
            auditLogMessage.setUserAgent(sourceRecord.userAgent());
            auditLogMessage.setIpAddress(sourceRecord.ipAddress());
            auditLogMessage.setContentType(sourceRecord.contentType());
            auditLogMessage.setPath(sourceRecord.path());
            auditLogMessage.setContentType(sourceRecord.contentType());
        }

        Object result = null;
        try {
            result = joinPoint.proceed();
            auditLogMessage.setOperationResult(OperationResult.SUCCESS);
            return result;
        } catch (Exception e) {
            String message = e.getMessage();
            if (e instanceof AccessDeniedException) {
                String resourceName = AuthHelper.getDeniedPermissionResource();
                String resourceActionName = AuthHelper.getDeniedPermissionResourceAction();
                message = "%s. Required '%s' action permission for resource '%s'".formatted(message, BeautificationUtil.camelToHumanForm(resourceActionName), Resource.findByCode(resourceName).getLabel());
            }
            auditLogMessage.setOperationResult(OperationResult.FAILURE);
            auditLogMessage.setMessage(message);
            throw e;
        } finally {
            constructLogData(annotation, auditLogMessage, signature.getMethod().getParameters(), joinPoint.getArgs(), result);
            auditLogsProducer.produceMessage(auditLogMessage);
        }
    }

    private void constructLogData(AuditLogged annotation, AuditLogMessage auditLogMessage, Parameter[] parameters, Object[] parameterValues, Object response) {
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
        auditLogMessage.setOperation(operation);
        constructResourceRecord(false, resource, resourceUuids, annotation.name().isEmpty() ? resourceName : annotation.name(), auditLogMessage);
        if (affiliatedResource != Resource.NONE) {
            constructResourceRecord(true, affiliatedResource, affiliatedResourceUuids, affiliatedResourceName, auditLogMessage);
        }
        auditLogMessage.setOperationData(responseOperationData);
        if (!data.isEmpty()) {
            auditLogMessage.setAdditionalData(data);
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

    private void constructResourceRecord(boolean affiliated, Resource resource, List<UUID> resourceUuids, String resourceName, AuditLogMessage auditLogMessage) {
        List<NameAndUuid> nameAndUuids = new ArrayList<>();

        // Work on a mutable set for fast removals
        Set<UUID> remainingUuids = resourceUuids == null ? new HashSet<>() : new HashSet<>(resourceUuids);

        // Add the "main" resource if present
        if (!remainingUuids.isEmpty()) {
            UUID first = remainingUuids.iterator().next();
            nameAndUuids.add(new NameAndUuid(resourceName, first));
            remainingUuids.remove(first);
        }

        // Merge with stored record
        ResourceRecord storedResource = LoggingHelper.getLogResourceInfo(affiliated);
        if (storedResource != null && storedResource.type() == resource) {
            List<NameAndUuid> storedNamesAndUuids = storedResource.nameAndUuids();
            if (storedNamesAndUuids != null) {
                for (NameAndUuid stored : storedNamesAndUuids) {
                    UUID uuid = stored.getUuid();
                    if (remainingUuids.contains(uuid) && stored.getName() != null) {
                        // Prefer the stored name if present
                        nameAndUuids.add(stored);
                        remainingUuids.remove(uuid);
                    } else if (!nameAndUuids.contains(stored)) {
                        // Only add if not already present
                        nameAndUuids.add(stored);
                    }
                }
            }
        }

        // Add all remaining UUIDs without names
        for (UUID uuid : remainingUuids) {
            nameAndUuids.add(new NameAndUuid(null, uuid));
        }
        if (affiliated) {
            auditLogMessage.setAffiliatedResource(resource);
            auditLogMessage.setAffiliatedResourceNamesAndUuids(nameAndUuids);
        } else {
            auditLogMessage.setResource(resource);
            auditLogMessage.setResourceNamesAndUuids(nameAndUuids);
        }
    }

}
