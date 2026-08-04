package com.otilm.core.util;

/**
 * The fixed ports test-local WireMock stubs bind, and that the matching {@code @TestPropertySource} points the
 * application at. Surefire runs test classes sequentially in one fork, so a fixed port is safe; a fixed port below the
 * 49152 ephemeral floor is also immune to the collisions {@code dynamicPort()} suffers when another process on the
 * machine happens to hold the port it drew.
 * <p>
 * Ports are grouped by the service they mock and by the theme of the tests that use them. Classes that share one cached
 * Spring context must spell the property identically — {@link com.otilm.core.architecture.ContextSignature} compares the
 * annotation's source text — so they necessarily share a constant; but a constant may also be reused by classes that
 * each load their own context. That reuse is safe because Surefire runs test classes sequentially and every class stops
 * its server when it finishes.
 */
public final class WireMockPorts {

    /** auth-service stub for the main group of service-layer and event-handling integration tests. */
    public static final int AUTH_SERVICE = 10001;

    /** auth-service stub for the security-chain tests, which each load their own context with an empty context-path. */
    public static final int AUTH_SERVICE_SECURITY_CHAIN = 10003;

    /** auth-service stub for tests outside those two groups, which each load their own context. */
    public static final int AUTH_SERVICE_STANDALONE = 10000;

    /** provisioning-api stub. */
    public static final int PROVISIONING_API = 10010;

    /** scheduler stub. */
    public static final int SCHEDULER = 10011;

    private WireMockPorts() {
    }
}
