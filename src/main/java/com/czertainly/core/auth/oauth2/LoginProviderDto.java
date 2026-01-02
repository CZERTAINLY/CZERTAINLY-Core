package com.czertainly.core.auth.oauth2;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Data transfer object representing a login provider that can be used to
 * authenticate users. This DTO is exposed in API responses to describe
 * available authentication mechanisms (e.g. external identity providers).
 */
@Data
public class LoginProviderDto {

    /**
     * Human-readable identifier or display name of the login provider.
     */
    @NotNull
    private String name;

    /**
     * URL that clients should use to initiate the login flow with this provider.
     */
    @NotNull
    private String loginUrl;
}
