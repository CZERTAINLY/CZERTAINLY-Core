package com.czertainly.core.logging;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.ActorType;
import com.czertainly.api.model.core.logging.enums.AuthMethod;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.records.ActorRecord;
import com.czertainly.api.model.core.logging.records.ResourceObjectIdentity;
import com.czertainly.api.model.core.logging.records.ResourceRecord;
import com.czertainly.api.model.core.logging.records.SourceRecord;
import com.czertainly.core.util.NullUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.util.List;
import java.util.stream.Collectors;

public class LoggingHelper {

    private static final String LOG_AUDIT_OPERATION = "log_audit_operation";
    private static final String LOG_AUDIT_RESOURCE = "log_audit_resource";
    private static final String LOG_AUDIT_RESOURCE_UUID = "log_audit_resource_uuid";
    private static final String LOG_AUDIT_RESOURCE_NAME = "log_audit_resource_name";
    private static final String LOG_AUDIT_AFFILIATED_RESOURCE = "log_audit_affiliatedResource";
    private static final String LOG_AUDIT_AFFILIATED_RESOURCE_UUID = "log_audit_affiliatedResource_uuid";
    private static final String LOG_AUDIT_AFFILIATED_RESOURCE_NAME = "log_audit_affiliatedResource_name";

    private static final String LOG_ACTOR_TYPE = "log_actor_type";
    private static final String LOG_ACTOR_AUTH_METHOD = "log_actor_authMethod";
    private static final String LOG_ACTOR_UUID = "log_actor_uuid";
    private static final String LOG_ACTOR_NAME = "log_actor_name";

    private static final String LOG_SOURCE_METHOD = "log_source_method";
    private static final String LOG_SOURCE_PATH = "log_source_path";
    private static final String LOG_SOURCE_CONTENT_TYPE = "log_source_contentType";
    private static final String LOG_SOURCE_IP_ADDRESS = "log_source_ipAddress";
    private static final String LOG_SOURCE_USER_AGENT = "log_source_userAgent";

    private LoggingHelper() {

    }

    public static Operation getAuditLogOperation() {
        String operation = MDC.get(LoggingHelper.LOG_AUDIT_OPERATION);
        return operation == null ? Operation.UNKNOWN : Operation.valueOf(operation);
    }

    public static ActorRecord getActorInfo() {
        String actor = MDC.get(LOG_ACTOR_TYPE);
        if (actor == null) {
            return new ActorRecord(ActorType.CORE, AuthMethod.NONE, null, null);
        } else {
            return ActorRecord.builder()
                    .type(ActorType.valueOf(actor))
                    .authMethod(AuthMethod.valueOf(MDC.get(LOG_ACTOR_AUTH_METHOD)))
                    .uuid(NullUtil.parseUuidOrNull(MDC.get(LOG_ACTOR_UUID)))
                    .name(MDC.get(LOG_ACTOR_NAME))
                    .build();
        }
    }

    public static ActorType getActorType() {
        return ActorType.valueOf(MDC.get(LOG_ACTOR_TYPE));
    }

    public static SourceRecord getSourceInfo() {
        String method = MDC.get(LOG_SOURCE_METHOD);
        if (method != null) {
            return SourceRecord.builder()
                    .method(method)
                    .path(MDC.get(LOG_SOURCE_PATH))
                    .contentType(MDC.get(LOG_SOURCE_CONTENT_TYPE))
                    .ipAddress(MDC.get(LOG_SOURCE_IP_ADDRESS))
                    .userAgent(MDC.get(LOG_SOURCE_USER_AGENT))
                    .build();
        }
        return null;
    }

    public static ResourceRecord getLogResourceInfo(boolean affiliated) {
        ResourceRecord resourceRecord = null;
        if (affiliated) {
            String resource = MDC.get(LOG_AUDIT_AFFILIATED_RESOURCE);
            if (resource != null) {
                resourceRecord = new ResourceRecord(Resource.valueOf(resource), List.of(new ResourceObjectIdentity(MDC.get(LOG_AUDIT_AFFILIATED_RESOURCE_NAME), NullUtil.parseUuidOrNull(MDC.get(LOG_AUDIT_AFFILIATED_RESOURCE_UUID)))));
            }
        } else {
            String resource = MDC.get(LOG_AUDIT_RESOURCE);
            if (resource != null) {
                resourceRecord = new ResourceRecord(Resource.valueOf(resource), List.of(new ResourceObjectIdentity(MDC.get(LOG_AUDIT_RESOURCE_NAME), NullUtil.parseUuidOrNull(MDC.get(LOG_AUDIT_RESOURCE_UUID)))));
            }
        }

        return resourceRecord;
    }

    public static void putAuditLogOperation(Operation operation) {
        MDC.put(LoggingHelper.LOG_AUDIT_OPERATION, operation.toString());
    }

    public static void putSourceInfo(HttpServletRequest request) {
        MDC.put(LoggingHelper.LOG_SOURCE_METHOD, request.getMethod());
        MDC.put(LoggingHelper.LOG_SOURCE_PATH, request.getRequestURI());
        MDC.put(LoggingHelper.LOG_SOURCE_CONTENT_TYPE, request.getContentType());
        MDC.put(LoggingHelper.LOG_SOURCE_IP_ADDRESS, getClientIPAddress(request));
        MDC.put(LoggingHelper.LOG_SOURCE_USER_AGENT, request.getHeader("User-Agent"));
    }

    public static void putActorInfoWhenNull(ActorType actorType, AuthMethod authMethod) {
        if (actorType != null) {
            String actualActorType = MDC.get(LOG_ACTOR_TYPE);
            if (actualActorType == null) {
                MDC.put(LoggingHelper.LOG_ACTOR_TYPE, actorType.name());
            }
        }
        if (authMethod != null) {
            String actualAuthMethod = MDC.get(LOG_ACTOR_AUTH_METHOD);
            if (actualAuthMethod == null) {
                MDC.put(LoggingHelper.LOG_ACTOR_AUTH_METHOD, authMethod.name());
            }
        }
    }

    public static void putActorInfoWhenNull(ActorType actorType, String actorUuid, String actorName) {
        if (actorType != null) MDC.put(LoggingHelper.LOG_ACTOR_TYPE, actorType.name());
        if (actorUuid != null) MDC.put(LoggingHelper.LOG_ACTOR_UUID, actorUuid);
        if (actorName != null) MDC.put(LoggingHelper.LOG_ACTOR_NAME, actorName);
    }

    public static void putLogResourceInfo(Resource resource, boolean affiliated, String resourceUuid, String resourceName) {
        if (affiliated) {
            MDC.put(LoggingHelper.LOG_AUDIT_AFFILIATED_RESOURCE, resource.name());
            if (resourceUuid != null) MDC.put(LoggingHelper.LOG_AUDIT_AFFILIATED_RESOURCE_UUID, resourceUuid);
            if (resourceName != null) MDC.put(LoggingHelper.LOG_AUDIT_AFFILIATED_RESOURCE_NAME, resourceName);
        } else {
            MDC.put(LoggingHelper.LOG_AUDIT_RESOURCE, resource.name());
            if (resourceUuid != null) MDC.put(LoggingHelper.LOG_AUDIT_RESOURCE_UUID, resourceUuid);
            if (resourceName != null) MDC.put(LoggingHelper.LOG_AUDIT_RESOURCE_NAME, resourceName);
        }
    }

    // Method to handle extracting the client IP, even if behind proxies
    private static String getClientIPAddress(HttpServletRequest request) {
        String ipAddress = null;
        List<String> proxyHeaders = List.of("X-Forwarded-For", "HTTP_X_FORWARDED_FOR", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP");

        for (String proxyHeader : proxyHeaders) {
            ipAddress = request.getHeader(proxyHeader);
            if (ipAddress != null && !ipAddress.isBlank() && !ipAddress.equalsIgnoreCase("unknown")) {
                break;
            }
        }

        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }

        // In case of multiple proxies, the first IP in the list is the real client IP
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0];
        }
        return ipAddress;
    }

    public static String formatResourceObjectForCsv(List<ResourceObjectIdentity> items) {
        if (items == null || items.isEmpty()) return "";
        String result = items.stream()
                .map(i -> String.format("{%s;%s}", i.name(), i.uuid() == null ? null : i.uuid().toString()))
                .collect(Collectors.joining(";"));
        return "[%s]".formatted(result);
    }

}
