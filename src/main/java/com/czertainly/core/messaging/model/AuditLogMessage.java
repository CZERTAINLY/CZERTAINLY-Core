package com.czertainly.core.messaging.model;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.*;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.records.*;
import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class AuditLogMessage {

    private String version;
    private Module module;
    private Resource resource;
    private List<NameAndUuid> resourceNamesAndUuids;
    private Resource affiliatedResource;
    private List<NameAndUuid> affiliatedResourceNamesAndUuids;
    private ActorType actorType;
    private AuthMethod actorAuthMethod;
    private UUID actorUuid;
    private String actorName;
    private String ipAddress;
    private String userAgent;
    private Operation operation;
    private OperationResult operationResult;
    private String message;
    private Serializable operationData;
    private Map<String, Object> additionalData;
    private String method;
    private String path;
    private String contentType;


}
