package com.czertainly.core.messaging.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum EventTypeEnum {

    EVENTS("events"),
    NOTIFICATION("notifications");

    EventTypeEnum(String value) {
        this.value = value;
    }

    @JsonValue
    private String value;

}

