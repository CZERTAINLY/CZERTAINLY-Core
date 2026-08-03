package com.otilm.core.util;

/**
 * The fixed ports test-local WireMock stubs bind, and that the matching {@code @TestPropertySource} points the
 * application at. Surefire runs test classes sequentially in one fork, so a fixed port is safe; a fixed port below the
 * 49152 ephemeral floor is also immune to the collisions {@code dynamicPort()} suffers when another process on the
 * machine happens to hold the port it drew.
 * <p>
 * Both halves — the {@code @TestPropertySource} URL and the {@code new WireMockServer(...)} call — must read the port
 * from here. Spelling the number twice per class lets one copy drift, and the failure mode is the application quietly
 * calling a port nothing is listening on: a timeout, not a clear error. The constants are {@code public static final}
 * so a concatenation such as {@code "auth-service.base-url=http://localhost:" + WireMockPorts.AUTH_SERVICE} is a
 * compile-time constant expression, which an annotation value must be. A class's own {@code private} constant cannot
 * serve: a type-level annotation sits outside the class body's scope and cannot read it.
 * <p>
 * A port is shared by a cohort, not global. Classes that share one cached Spring context must spell the property
 * identically — {@link com.otilm.core.architecture.ContextSignature} compares the annotation's source text — so they
 * share a constant by necessity. Classes that share a context with nobody get their own port instead: it buys them
 * nothing to join a crowded one, and it widens the blast radius when a stub outlives its test.
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
