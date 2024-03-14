package com.czertainly.core.enums;

import com.czertainly.api.model.client.connector.ConnectDto;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.entity.scep.ScepProfile;

public enum ResourceToClass {

    NONE(Resource.NONE, null),

    // GENERAL
    DASHBOARD(Resource.DASHBOARD, null),
    SETTINGS(Resource.SETTINGS, Setting.class),
    AUDIT_LOG(Resource.AUDIT_LOG, AuditLog.class),
    CREDENTIAL(Resource.CREDENTIAL, Credential.class),
    CONNECTOR(Resource.CONNECTOR, Connector.class),
    ATTRIBUTE(Resource.ATTRIBUTE, BaseAttribute.class),
    SCHEDULED_JOB(Resource.SCHEDULED_JOB, ScheduledJob.class),
    NOTIFICATION_INSTANCE(Resource.NOTIFICATION_INSTANCE, Notification.class),

    // AUTH
    USER(Resource.USER, UserDto.class),
    ROLE(Resource.ROLE, RoleDto.class),

    // ACME
    ACME_ACCOUNT(Resource.ACME_ACCOUNT, AcmeAccount.class),
    ACME_PROFILE(Resource.ACME_PROFILE, AcmeProfile.class),

    //SCEP
    SCEP_PROFILE(Resource.SCEP_PROFILE, ScepProfile.class),

    // CERTIFICATES
    AUTHORITY(Resource.AUTHORITY, AuthorityInstanceReference.class),
    RA_PROFILE(Resource.RA_PROFILE, RaProfile.class),
    CERTIFICATE(Resource.CERTIFICATE, Certificate.class),
    GROUP(Resource.GROUP, Group.class),
    COMPLIANCE_PROFILE(Resource.COMPLIANCE_PROFILE, ComplianceProfile.class),
    DISCOVERY(Resource.DISCOVERY, DiscoveryHistory.class),

    // ENTITIES
    ENTITY(Resource.ENTITY, EntityInstanceReference.class),
    LOCATION(Resource.LOCATION, Location.class),

    //CRYPTOGRAPHY
    TOKEN_PROFILE(Resource.TOKEN_PROFILE, TokenProfile.class),
    TOKEN(Resource.TOKEN, TokenInstanceReference.class),
    CRYPTOGRAPHIC_KEY(Resource.CRYPTOGRAPHIC_KEY, CryptographicKey.class),

    // APPROVALS
    APPROVAL_PROFILE(Resource.APPROVAL_PROFILE, ApprovalProfile.class),
    APPROVAL(Resource.APPROVAL, Approval.class),
    ;


    private final Resource resource;
    private final Class aClass;

    ResourceToClass(final Resource resource, final Class aClass) {
        this.resource = resource;
        this.aClass = aClass;
    }

    public Resource getResource() {
        return resource;
    }

    public Class getaClass() {
        return aClass;
    }

    public static Class getClassByResource(final Resource resource) {
        for (ResourceToClass resourceToClass : ResourceToClass.values()) {
            if (resourceToClass.getResource().equals(resource)) {
                return resourceToClass.getaClass();
            }
        }
        return null;
    }
}
