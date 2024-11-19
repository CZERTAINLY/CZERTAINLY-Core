package com.czertainly.core.logging;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.*;
import com.czertainly.api.model.core.logging.enums.Module;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@JsonPropertyOrder({"id", "version", "loggedAt", "module", "operation", "operationResult",
        "resource", "resourceUuids", "resourceNames",
        "affiliatedResource", "affiliatedResourceUuids", "affiliatedResourceNames",
        "actorType", "actorAuthMethod", "actorUuid", "actorName",
        "ipAddress", "userAgent", "message", "operationData", "additionalData"})
public record AuditLogExportDto(
        long id,
        String version,
        OffsetDateTime loggedAt,
        Module module,
        Resource resource,
        List<UUID> resourceUuids,
        List<String> resourceNames,
        Resource affiliatedResource,
        List<UUID> affiliatedResourceUuids,
        List<String> affiliatedResourceNames,
        ActorType actorType,
        AuthMethod actorAuthMethod,
        UUID actorUuid,
        String actorName,
        String ipAddress,
        String userAgent,
        Operation operation,
        OperationResult operationResult,
        String message,
        String operationData,
        String additionalData
) implements Serializable {
}
