package com.czertainly.core.security.authz;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SecuredParentUUID extends SecuredUUID {
    protected SecuredParentUUID(UUID value) {
        super(value);
    }

    protected SecuredParentUUID(String value) {
        super(value);
    }

    public static SecuredParentUUID fromString(String value) {
        return new SecuredParentUUID(value);
    }

    public static SecuredParentUUID fromUUID(UUID value) {
        return new SecuredParentUUID(value);
    }
}
