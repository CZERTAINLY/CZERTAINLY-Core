package com.otilm.core.service.registration;

/**
 * The stored registration challenge cannot be used as the key material a protocol verifier needs (for example an
 * operator secret shorter than the HS256 minimum). A configuration fault of the registration, not a wrong credential:
 * thrown from inside the gate's predicate so the evaluation is abandoned without counting an attempt.
 */
public class UnusableRegistrationChallengeException extends RuntimeException {

    public UnusableRegistrationChallengeException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
