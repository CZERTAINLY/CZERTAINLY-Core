package com.czertainly.core.logging;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.*;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.records.ResourceObjectIdentity;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@JsonPropertyOrder({"id", "version", "loggedAt", "module", "operation", "operationResult",
        "resource", "resourceObjects",
        "affiliatedResource", "affiliatedObjects",
        "actorType", "actorAuthMethod", "actorUuid", "actorName",
        "ipAddress", "userAgent", "message", "operationData", "additionalData"})
public record AuditLogExportDto(
        long id,
        String version,
        OffsetDateTime loggedAt,
        Module module,
        Resource resource,
        List<ResourceObjectIdentity> resourceObjects,
        Resource affiliatedResource,
        List<ResourceObjectIdentity> affiliatedObjects,
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
