package com.czertainly.core.security.authz;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SecuredUUID {

    private final String value;

    protected SecuredUUID(String value) {
        this.value = value;
    }

    public static SecuredUUID fromString(String value) {
        return new SecuredUUID(value);
    }

    public static SecuredUUID fromUUID(UUID value) {
        return fromString(value.toString());
    }

    public static List<SecuredUUID> fromList(List<String> values) {
        return values.stream().map(SecuredUUID::fromString).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return this.value;
    }
}
