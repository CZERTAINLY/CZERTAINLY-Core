package com.czertainly.core.model.auth;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;

public enum Resource {

    DASHBOARD("dashboard"),
    RA_PROFILE("raProfile"),

    // TODO Check and remove client and admin after new authorization
    CLIENT("client"),
    ADMIN("admin"),

    CONNECTOR("connector"),
    CREDENTIAL("credential"),
    AUTHORITY("authority"),
    ACME_ACCOUNT("acmeAccount"),
    ACME_PROFILE("acmeProfile"),
    GROUP("group"),
    DISCOVERY("discovery"),
    CERTIFICATE("certificate"),
    AUDIT_LOG("auditLog"),
    COMPLIANCE_PROFILE("complianceProfile"),
    ENTITY("entity"),
    LOCATION("location");


    @Schema(description = "Resource Name",
            example = "client",
            required = true)
    private String code;

    Resource(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return this.code;
    }

    @JsonCreator
    public static Resource findByCode(String code) {
        return Arrays.stream(Resource.values())
                .filter(k -> k.code.equals(code))
                .findFirst()
                .orElseThrow(() ->
                        new ValidationException(ValidationError.create("Unknown Resource Name {}", code)));
    }
}
