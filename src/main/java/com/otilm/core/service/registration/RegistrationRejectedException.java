package com.otilm.core.service.registration;

/**
 * A protocol enrolment did not resolve to an eligible, verified certificate registration. The message is the reason for
 * the log only; each protocol answers the wire with its own single generic rejection.
 */
public class RegistrationRejectedException extends Exception {

    public RegistrationRejectedException(String reason) {
        super(reason);
    }
}
