package com.otilm.core.util;

/**
 * Fixed ports for the WireMock stubs that stand in for external services, plus the property assignments that bind
 * those services to them.
 * <p>
 * Surefire runs test classes sequentially in one fork, so a fixed port is safe; a fixed port below the 49152 ephemeral
 * floor is also immune to the collisions {@code dynamicPort()} suffers when another process asks for an ephemeral port.
 * Enabling parallel test execution would break both properties — see the note on the surefire plugin in {@code pom.xml}.
 * <p>
 * Naming the ports globally rather than per class is what keeps them off the context signature: a per-class
 * {@code @TestPropertySource} would fork a context (see {@link com.otilm.core.architecture.ContextSignature}), whereas
 * the {@code *_URL_PROPERTY} assignments below are declared once on {@link BaseSpringBootTest} and
 * {@link BaseSpringBootTestNoAuth}, so every subclass inherits identical text and none of them forks. Classes stubbing
 * the same service therefore share one port; whether they also share a cached context depends on their other context
 * axes — the port is not one of them, which is the point. Every such class stops its server when it finishes.
 * <p>
 * A new constant is warranted only when two services must be stubbed with conflicting behaviour at the same time.
 * Two classes stubbing the <em>same</em> service never need separate ports, because they never run concurrently.
 * <p>
 * The test {@code application.yml} repeats these ports as literals — it cannot reference constants — as the floor for
 * the context-loading tests that extend neither base class. {@code WireMockPortsGuardTest} pins the two in sync.
 */
public final class WireMockPorts {

    /** auth-service stub. */
    public static final int AUTH_SERVICE = 10001;

    /** provisioning-api stub. */
    public static final int PROVISIONING_API = 10010;

    /** scheduler stub. */
    public static final int SCHEDULER = 10011;

    /** Property key for the auth-service base URL. */
    public static final String AUTH_SERVICE_URL_KEY = "auth-service.base-url";

    /** Property key for the scheduler base URL. */
    public static final String SCHEDULER_URL_KEY = "scheduler.base-url";

    /** Property key for the provisioning-api URL. */
    public static final String PROVISIONING_API_URL_KEY = "provisioning.api.url";

    /** Binds auth-service to its stub. Declared on the base test classes, above any {@code AUTH_SERVICE_BASE_URL}. */
    public static final String AUTH_SERVICE_URL_PROPERTY = AUTH_SERVICE_URL_KEY + "=http://localhost:" + AUTH_SERVICE;

    /** Binds the scheduler to its stub. Declared on the base test classes, above any {@code SCHEDULER_BASE_URL}. */
    public static final String SCHEDULER_URL_PROPERTY = SCHEDULER_URL_KEY + "=http://localhost:" + SCHEDULER;

    /** Binds provisioning-api to its stub. Declared on the base test classes, above any {@code PROVISIONING_API_URL}. */
    public static final String PROVISIONING_API_URL_PROPERTY =
            PROVISIONING_API_URL_KEY + "=http://localhost:" + PROVISIONING_API;

    private WireMockPorts() {
    }
}
