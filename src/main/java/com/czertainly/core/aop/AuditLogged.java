package com.czertainly.core.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLogged {

    ObjectType originator();
    ObjectType affected();
    OperationType operation();
}
