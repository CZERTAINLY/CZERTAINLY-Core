package com.czertainly.core.security.authn.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserDto {
    @JsonProperty("username")
    private String username;
    @JsonProperty("enabled")
    private boolean enabled;

    public UserDto() {
    }

    public String getUsername() {
        return username;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
