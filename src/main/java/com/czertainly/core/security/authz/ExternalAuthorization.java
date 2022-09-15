package com.czertainly.core.security.authz;

import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExternalAuthorization {
    Resource resource();

    ResourceAction action() default ResourceAction.NONE;

    Resource parentResource() default Resource.NONE;

    ResourceAction parentAction() default ResourceAction.NONE;

    Class<? extends ParentUUIDGetter> parentObjectUUIDGetter() default NoOpParentUUIDGetter.class;
}
