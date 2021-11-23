package com.czertainly.core.aop;

import com.czertainly.api.core.modal.ObjectType;

public class RequestContextHolder {
    private static final ThreadLocal<RequestContext> contextHolder = new ThreadLocal<>();

    private RequestContextHolder() {

    }

    public static RequestContext getContext() {
        RequestContext ctx = contextHolder.get();
        if (ctx == null) {
            ctx = createEmptyContext();
            contextHolder.set(ctx);
        }

        return ctx;
    }

    public static void setContext(ObjectType originator, ObjectType affected, Object identifier) {
        setContext(new RequestContext(originator, affected, identifier));
    };

    public static void setContext(RequestContext context) {
        contextHolder.set(context);
    }

    public static void clearContext() {
        contextHolder.remove();
    }

    public static RequestContext createEmptyContext() {
        return new RequestContext();
    }
}
