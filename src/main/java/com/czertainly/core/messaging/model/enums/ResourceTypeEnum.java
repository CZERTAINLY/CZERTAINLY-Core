package com.czertainly.core.messaging.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ResourceTypeEnum {

    CERTIFICATE("certificate");

    @JsonValue
    private String value;

    ResourceTypeEnum(String value) {
        this.value = value;
    }
}
