package com.czertainly.core.model.auth;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;

public enum ResourceAction {

    NONE("none"),

    //Common List and Detail actions
    LIST("list"),
    DETAIL("detail"),

    //CRUD Actions
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    FORCE_DELETE("forceDelete"),

    ENABLE("enable"),
    DISABLE("disable"),
    ACTIVATE("activate"),
    DEACTIVATE("deactivate"),

    //RA Profile actions - Check and remove after new authorization implementation
    AUTHORIZE_CLIENT("authorizeClient"),
    UNAUTHORIZE_CLIENT("unauthorizeClient"),

    //Connector actions
    RECONNECT("reconnect"),
    APPROVE("approve"),
    CONNECT("connect"),
    CALLBACK("callback"),
    REGISTER("register"),

    //Certificate actions
    UPLOAD("upload"),
    DOWNLOAD("download"),
    RENEW("renew"),
    REVOKE("revoke"),
    ISSUE("ISSUE"),


    //TODO Check if the below actions can be united into a single action permission on the certificate
    UPDATE_GROUP("updateGroup"),
    UPDATE_OWNER("updateOwner"),
    UPDATE_RA_PROFILE("updateRaProfile"),

    //Legacy EJBCA operations
    LIST_ENTITY_PROFILE("listEntityProfile"),
    LIST_CERTIFICATE_PROFILE("listCertificateProfile"),
    LIST_CERTIFICATE_AUTHORITY("listCertificateAuthority"),
    LIST_END_ENTITY("listEndEntity"),
    ADD_END_ENTITY("addEndEntity"),
    REVOKE_DELETE_END_ENTITY("revokeDeleteEndEntity"),
    END_ENTITY_DETAIL("endEntityDetail"),
    EDIT_END_ENTITY("editEndEntity"),
    RESET_PASSWORD("resetPassword"),

    VALIDATE("validate"),
    LIST_EVENT_HISTORY("listEventHistory"),
    LIST_LOCATION("listLocation"),

    //Audit Log export
    EXPORT("export"),

    //Certificate, RA Profile and Compliance Profile
    CHECK_COMPLIANCE("checkCompliance"),

    //RA Profile action to get the list of Clients - Can be removed after new Authorization
    LIST_AUTHORIZATIONS("listAuthorizations"),

    //Compliance Profile
    ADD_RULE("addRule"),
    REMOVE_RULE("removeRule"),
    ADD_GROUP("addGroup"),
    REMOVE_GROUP("removeGroup"),
    LIST_ASSOCIATED_RA_PROFILE("listAssociatedRaProfile"),
    LIST_RULE("listRule"),
    LIST_GROUP("listGroup"),

    // Compliance Profile and ACME Profile
    ASSOCIATE_RA_PROFILE("associateRaProfile"),
    DISASSOCIATE_RA_PROFILE("disassociateRaProfile"),

    // Entities and Locations
    ADD("add"),
    PUSH("push"),
    REMOVE_CERTIFICATE_FROM_LOCATION("removeCertificateFromLocation"),
    ISSUE_CERTIFICATE_TO_LOCATION("issueCertificateToLocation"),

    // RA Profile actions
    ACTIVATE_ACME("activateAcme"),
    DEACTIVATE_ACME("deactivateAcme"),
    ACME_DETAIL("acmeDetail"),
    ;

    @Schema(description = "Resource Action Name",
            example = "create",
            required = true)
    private String code;

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
