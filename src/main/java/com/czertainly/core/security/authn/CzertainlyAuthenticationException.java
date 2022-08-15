package com.czertainly.core.security.authn;

import org.springframework.security.core.AuthenticationException;

public class CzertainlyAuthenticationException extends AuthenticationException {

    public CzertainlyAuthenticationException(String msg) {
        super(msg);
    }

    public CzertainlyAuthenticationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
