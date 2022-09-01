package com.czertainly.core.security.authz;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SecuredUUID {

    private final UUID value;

    protected SecuredUUID(UUID value) {
        this.value = value;
    }

    protected SecuredUUID(String value) {
        this.value = UUID.fromString(value);
    }

    public static SecuredUUID fromString(String value) {
        if(value == null){
            return null;
        }
        return new SecuredUUID(value);
    }

    public static SecuredUUID fromUUID(UUID value) {
        return new SecuredUUID(value);
    }

    public static List<SecuredUUID> fromList(List<String> values) {
        return values.stream().map(SecuredUUID::fromString).collect(Collectors.toList());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
