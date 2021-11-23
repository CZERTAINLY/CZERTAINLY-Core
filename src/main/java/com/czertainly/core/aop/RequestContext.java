package com.czertainly.core.aop;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.czertainly.api.core.modal.ObjectType;

public class RequestContext {

    private ObjectType originator;
    private ObjectType affected;
    private Object identifier;

    public RequestContext() {
    }

    public RequestContext(ObjectType originator, ObjectType affected, Object identifier) {
        this.originator = originator;
        this.affected = affected;
        this.identifier = identifier;
    }

    public ObjectType getOriginator() {
        return originator;
    }

    public void setOriginator(ObjectType originator) {
        this.originator = originator;
    }

    public ObjectType getAffected() {
        return affected;
    }

    public void setAffected(ObjectType affected) {
        this.affected = affected;
    }

    public Object getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Object identifier) {
        this.identifier = identifier;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("originator", originator)
                .append("affected", affected)
                .append("identifier", identifier)
                .toString();
    }
}
