package com.czertainly.core.auth;

import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthEndpoint {
    Resource resourceName();
}