package com.czertainly.core.security.authn.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public class UserDetailsDto {
    @JsonProperty("user")
    UserDto user;
    @JsonProperty("roles")
    Collection<String> roles;

    public UserDetailsDto() {
    }

    public UserDto getUser() {
        return user;
    }

    public Collection<String> getRoles() {
        return roles;
    }
}
