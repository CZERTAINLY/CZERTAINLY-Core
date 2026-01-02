package com.czertainly.core.auth.oauth2;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginProviderDto {

    @NotNull
    private String name;

    @NotNull
    private String loginUrl;
}
