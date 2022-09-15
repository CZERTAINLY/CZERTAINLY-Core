package com.czertainly.core.security.authn.client;

import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.stream.Collectors;

public class AuthenticationInfo {

    private final String username;
    private final List<GrantedAuthority> authorities;
    private final String rawData;

    public AuthenticationInfo(String username, List<GrantedAuthority> authorities, String rawData) {
        this.username = username;
        this.authorities = authorities;
        this.rawData = rawData;
    }

    public AuthenticationInfo(String username, List<GrantedAuthority> authorities) {
        this.username = username;
        this.authorities = authorities;
        List<String> roles = authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());

        this.rawData = "{" +
                " \"user\": {\"username\":\"" + this.username + "\"}," +
                " \"roles\": [" + roles.stream().map(a -> "\"" + a +"\"").collect(Collectors.joining(",")) + "]" +
                "}";
    }

    public String getUsername() {
        return username;
    }

    public List<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public String getRawData() {
        return rawData;
    }
}
