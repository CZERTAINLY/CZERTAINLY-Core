package com.otilm.core.util;

/**
 * The fixed ports test-local WireMock stubs bind, and that {@code src/test/resources/application.yml} points the
 * application at for every test context. Surefire runs test classes sequentially in one fork, so a fixed port is safe;
 * a fixed port below the 49152 ephemeral floor is also immune to the collisions {@code dynamicPort()} suffers when
 * another process on the machine happens to hold the port it drew.
 * <p>
 * These constants and the URLs in the test {@code application.yml} must stay in sync — the yml cannot reference them.
 * Naming the ports globally rather than per class is what keeps them off the context signature: a per-class
 * {@code @TestPropertySource} would fork a context (see {@link com.otilm.core.architecture.ContextSignature}), whereas
 * the test-global yml is identical for every context and so forks nothing. Classes stubbing the same service therefore
 * share one port and one cached context; that is safe because every class stops its server when it finishes.
 */
public final class WireMockPorts {

    /** auth-service stub. */
    public static final int AUTH_SERVICE = 10001;

    /** provisioning-api stub. */
    public static final int PROVISIONING_API = 10010;

    /** scheduler stub. */
    public static final int SCHEDULER = 10011;

    private WireMockPorts() {
    }
}
