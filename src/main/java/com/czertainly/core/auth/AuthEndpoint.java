package com.czertainly.core.auth;

import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthEndpoint {

    ResourceName resourceName();
    ActionName actionName();
    boolean isListingEndPoint() default false;

}
