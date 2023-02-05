package com.czertainly.core.aop;

import com.czertainly.api.model.common.Identified;
import com.czertainly.api.model.common.Named;
import com.czertainly.api.model.core.audit.OperationStatusEnum;
import com.czertainly.core.service.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
@ConditionalOnExpression("${auditlog.enabled:true}")
public class AuditLogAspect {

    @Autowired
    private AuditLogService auditLogService;

    @Around("@annotation(AuditLogged)")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        // if in non-request context, do not log
        if(RequestContextHolder.getRequestAttributes() == null) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AuditLogged annotation = signature.getMethod().getAnnotation(AuditLogged.class);

        Map<Object, Object> additionalData = createAdditionalData(signature.getParameterNames(), joinPoint.getArgs());
        additionalData.put("method", signature.getName());

        String objectIdentifier = getRequestObjectIdentifier(signature.getParameterNames(), joinPoint.getArgs());

        OperationStatusEnum operationStatus = null;
        try {
            Object result = joinPoint.proceed();
            operationStatus = OperationStatusEnum.SUCCESS;

            if (objectIdentifier == null) {
                objectIdentifier = getResponseObjectIdentifier(result);
            }

            return result;
        } catch (Exception e) {
            operationStatus = OperationStatusEnum.FAILURE;
            throw e;
        } finally {
            auditLogService.log(annotation.originator(),
                    annotation.affected(),
                    objectIdentifier,
                    annotation.operation(),
                    operationStatus,
                    additionalData);
        }
    }

    private Map<Object, Object> createAdditionalData(String[] names, Object[] values) {
        Map<Object, Object> data = new LinkedHashMap<>();
        if (names != null && values != null) {
            for (int i = 0; i < names.length; i++) {
                data.put(names[i], values[i]);
            }
        }
        return data;
    }

    private String getRequestObjectIdentifier(String[] names, Object[] values) {
        if (names == null || names.length == 0 || values == null || values.length == 0) {
            return null;
        }

        if (values[0] instanceof Long && names[0].toLowerCase().endsWith("id")) {
            return values[0].toString();
        }

        if (values[0] instanceof String &&
                (names[0].toLowerCase().endsWith("name") ||
                 names[0].toLowerCase().endsWith("uuid"))) {
            return values[0].toString();
        }

        if (values[0] instanceof Identified) {
            return ((Identified) values[0]).getUuid();
        }

        if (values[0] instanceof Named) {
            return ((Named) values[0]).getName();
        }

        return null;
    }

    private String getResponseObjectIdentifier(Object result) {
        if (result instanceof Identified) {
            return ((Identified) result).getUuid();
        }

        if (result instanceof Named) {
            return ((Named) result).getName();
        }

        return null;
    }
}
