package com.czertainly.core.model.auth;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;

public enum ResourceAction {
    NONE("NONE"),
    ANY("ANY"), // Action that is evaluated as any action

    // Default (CRUD) Actions
    LIST("list"),
    DETAIL("detail"),
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),

    // Default change state actions that allows also reverse action (disable/deactivate)
    ENABLE("enable"),
    ACTIVATE("activate"),

    //RA Profile actions - Check and remove after new authorization implementation
    AUTHORIZE_CLIENT("authorizeClient"),
    UNAUTHORIZE_CLIENT("unauthorizeClient"),

    //Connector actions
    APPROVE("approve"),
    CONNECT("connect"), // allows also reconnect action

    //Certificate actions
    UPLOAD("upload"),
    RENEW("renew"),
    REVOKE("revoke"),
    ISSUE("issue"),

    //Legacy EJBCA operations - removed later
//    LIST_ENTITY_PROFILE("listEntityProfile"),
//    LIST_CERTIFICATE_PROFILE("listCertificateProfile"),
//    LIST_CERTIFICATE_AUTHORITY("listCertificateAuthority"),
//    LIST_END_ENTITY("listEndEntity"),
//    ADD_END_ENTITY("addEndEntity"),
//    REVOKE_DELETE_END_ENTITY("revokeDeleteEndEntity"),
//    END_ENTITY_DETAIL("endEntityDetail"),
//    EDIT_END_ENTITY("editEndEntity"),
//    RESET_PASSWORD("resetPassword"),

    // Audit Log export
    EXPORT("export"),

    //Certificate, RA Profile and Compliance Profile
    CHECK_COMPLIANCE("checkCompliance"),

    //RA Profile action to get the list of Clients - Can be removed after new Authorization
    LIST_AUTHORIZATIONS("listAuthorizations"),

    // RA Profile actions
    ACTIVATE_ACME("activateAcme"),
//    ACME_DETAIL("acmeDetail"), // used RA Profile detail action
    ;

    @Schema(description = "Resource Action Name",
            example = "create",
            required = true)
            
    private final String code;

    ResourceAction(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return this.code;
    }

    @JsonCreator
    public static ResourceAction findByCode(String code) {
        return Arrays.stream(ResourceAction.values())
                .filter(k -> k.code.equals(code))
                .findFirst()
                .orElseThrow(() ->
                        new ValidationException(ValidationError.create("Unknown Resource Action Name {}", code)));
    }

}
