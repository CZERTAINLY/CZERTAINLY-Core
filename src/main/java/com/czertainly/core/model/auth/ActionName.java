package com.czertainly.core.model.auth;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;

public enum ActionName {

    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    DETAIL("detail"),
    ENABLE("enable"),
    DISABLE("disable"),
    AUTHORIZE_CLIENT("authorizeClient"),
    UNAUTHORIZE_CLIENT("unauthorizeClient"),
    ACTIVATE("activate"),
    DEACTIVATE("deactivate"),
    RECONNECT("reconnect"),
    APPROVE("approve"),
    CONNECT("connect"),
    FORCE_DELETE("forceDelete"),
    UPLOAD("upload"),
    UPDATE_GROUP("updateGroup"),
    UPDATE_OWNER("updateOwner"),
    UPDATE_RA_PROFILE("updateRaProfile"),
    DOWNLOAD("download"),
    RENEW("renew"),
    REVOKE("revoke"),
    EXPORT("export"),
    LIST_ENTITY_PROFILE("listEntityProfile"),
    LIST_CERTIFICATE_PROFILE("listCertificateProfile"),
    LIST_CERTIFICATE_AUTHORITY("listCertificateAuthority"),
    LIST_ATTRIBUTES("listAttributes"),
    VALIDATE_ATTRIBUTES("validateAttributes"),
    CALLBACK("callback"),
    VALIDATE("validate"),
    LIST_EVENT_HISTORY("listEventHistory"),
    LIST_FILTER_OPTION("listFilterOption"),
    LIST_LOCATION("listLocation"),
    CHECK_COMPLIANCE("checkCompliance"),
    LIST_AUTHORIZATIONS("listAuthorizations"),
    ADD_RULE("addRule"),
    REMOVE_RULE("removeRule"),
    ADD_GROUP("addGroup"),
    REMOVE_GROUP("removeGroup"),
    LIST_ASSOCIATED_RA_PROFILE("listAssociatedRaProfile"),
    LIST_RULE("listRule"),
    LIST_GROUP("listGroup"),
    ASSOCIATE_RA_PROFILE("associateRaProfile"),
    DISASSOCIATE_RA_PROFILE("disassociateRaProfile"),
    LIST_AUTHENTICATION_TYPES("listAuthenticationTypes"),
    CHECK_HEALTH("checkHealth"),
    ADD("add"),
    PUSH("push"),
    REMOVE_CERTIFICATE_FROM_LOCATION("removeCertificateFromLocation"),
    ISSUE_CERTIFICATE_TO_LOCATION("issueCertificateToLocation"),
    ACME_DETAIL("acmeDetail"),
    ACTIVATE_ACME("activateAcme"),
    DEACTIVATE_ACME("deactivateAcme"),
    ISSUE("ISSUE"),
    LIST_END_ENTITY("listEndEntity"),
    ADD_END_ENTITY("addEndEntity"),
    REVOKE_DELETE_END_ENTITY("revokeDeleteEndEntity"),
    END_ENTITY_DETAIL("endEntityDetail"),
    EDIT_END_ENTITY("editEndEntity"),
    RESET_PASSWORD("resetPassword"),
    REGISTER("register")
    ;

    @Schema(description = "Action Name",
            example = "create",
            required = true)
    private String code;

    ActionName(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return this.code;
    }

    @JsonCreator
    public static ActionName findByCode(String code) {
        return Arrays.stream(ActionName.values())
                .filter(k -> k.code.equals(code))
                .findFirst()
                .orElseThrow(() ->
                        new ValidationException(ValidationError.create("Unknown Action Name {}", code)));
    }

}
