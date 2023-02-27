package com.czertainly.core.security.authn.client;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.stream.Collectors;

public class AuthenticationInfo {

    private static final String ANONYMOUS_USERNAME = "anonymousUser";
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

    public boolean isAnonymous() {
        return this.username.equals(ANONYMOUS_USERNAME);
    }

    public static AuthenticationInfo getAnonymousAuthenticationInfo() {
        return new AuthenticationInfo(ANONYMOUS_USERNAME, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    }
}
