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

    //Connector actions
    APPROVE("approve"),
    CONNECT("connect"), // allows also reconnect action

    //Certificate actions
    RENEW("renew"),
    REVOKE("revoke"),

    // Audit Log export
    EXPORT("export"),

    //Certificate, RA Profile and Compliance Profile
    CHECK_COMPLIANCE("checkCompliance"),

    // RA Profile actions
    ACTIVATE_ACME("activateAcme"),

    //CRYPTOGRAPHY OPERATION
    ENCRYPT("encrypt"),
    DECRYPT("decrypt"),
    VERIFY("verify"),
    SIGN("sign"),

    ISSUE("issue"),

    REKEY("rekey");

    @Schema(description = "Resource Action Name",
            example = "create",
            requiredMode = Schema.RequiredMode.REQUIRED)
            
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
