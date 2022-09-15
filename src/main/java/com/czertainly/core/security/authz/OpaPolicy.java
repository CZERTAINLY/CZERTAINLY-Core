package com.czertainly.core.security.authz;

public enum OpaPolicy {
    METHOD("method"),
    ENDPOINT("endpoint"),
    OBJECTS("objects");

    public final String policyName;

    OpaPolicy(String policyName) {
        this.policyName = policyName;
    }
}
