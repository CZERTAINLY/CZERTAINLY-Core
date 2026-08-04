package com.otilm.core.util;

/**
 * The fixed ports test-local WireMock stubs bind, and that the matching {@code @TestPropertySource} points the
 * application at. Surefire runs test classes sequentially in one fork, so a fixed port is safe; a fixed port below the
 * 49152 ephemeral floor is also immune to the collisions {@code dynamicPort()} suffers when another process on the
 * machine happens to hold the port it drew.
 */
public final class WireMockPorts {

    /** auth-service stub for the nine-class shared context group, plus {@code EventHandlersITest}. */
    public static final int AUTH_SERVICE = 10001;

    /** auth-service stub for the security-chain tests, which each load their own context with an empty context-path. */
    public static final int AUTH_SERVICE_SECURITY_CHAIN = 10003;

    /** auth-service stub for tests that share a context with no other class, and so need no shared port. */
    public static final int AUTH_SERVICE_STANDALONE = 10000;

    /** provisioning-api stub for the three-class shared context group. */
    public static final int PROVISIONING_API = 10010;

    /** scheduler stub. */
    public static final int SCHEDULER = 10011;

    private WireMockPorts() {
    }
}
