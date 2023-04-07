package com.czertainly.core.service.scep.exception;

import com.czertainly.api.model.core.scep.FailInfo;

public class ScepException extends Exception {

    private FailInfo failInfo;

    public ScepException() {
        super();
    }

    public ScepException(String message) {
        super(message);
    }

    public ScepException(String message, FailInfo failInfo) {
        super(message);
        this.failInfo = failInfo;
    }

    public ScepException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScepException(String message, Throwable cause, FailInfo failInfo) {
        super(message, cause);
        this.failInfo = failInfo;
    }

    public ScepException(Throwable cause) {
        super(cause);
    }

    public FailInfo getFailInfo() {
        return failInfo;
    }

    public void setFailInfo(FailInfo failInfo) {
        this.failInfo = failInfo;
    }
}
