package com.czertainly.core.provisioning;

/**
 * Exception thrown when proxy provisioning operations fail.
 */
public class ProxyProvisioningException extends RuntimeException {

    public ProxyProvisioningException(String message) {
        super(message);
    }

    public ProxyProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
