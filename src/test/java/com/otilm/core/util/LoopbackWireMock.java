package com.otilm.core.util;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extension;

/**
 * Starts WireMock stubs on an OS-chosen port bound to the IPv4 loopback address.
 * <p>
 * <b>Why not the wildcard</b> — {@code new WireMockServer(0)} asks the OS for a port free on {@code 0.0.0.0}, which it
 * grants without regard for ports already held on the more specific {@code 127.0.0.1}. Jetty sets SO_REUSEADDR, so the
 * overlapping bind succeeds; the loopback holder keeps winning the connection, and the stub never sees the request. The
 * failure surfaces as a plausible-looking HTTP response from an unrelated local listener, so it reads as a product bug
 * rather than an environment collision. Naming the loopback address makes the OS skip every port already taken there.
 * <p>
 * <b>Why not a fixed port</b> — a fixed port would couple every test class to every other one's port usage and cap the
 * stub at one live instance. Only a stub whose URL must be known before the Spring context exists needs one; those are
 * named in {@link WireMockPorts}. Everything here hands its URL to the code under test at runtime.
 *
 * @see #url(WireMockServer)
 */
public final class LoopbackWireMock {

    /**
     * Stubs bind, and callers reach them at, this literal address rather than {@code localhost}: that name resolves to
     * both {@code 127.0.0.1} and {@code ::1}, and a client picking the IPv6 form cannot reach an IPv4-bound server.
     */
    public static final String HOST = "127.0.0.1";

    private LoopbackWireMock() {
    }

    /**
     * Starts a server on a free loopback port, with any response-transformer extensions the caller needs registered.
     */
    public static WireMockServer start(Extension... extensions) {
        WireMockServer server = new WireMockServer(
                WireMockConfiguration.options().bindAddress(HOST).dynamicPort().extensions(extensions));
        server.start();
        return server;
    }

    /** The base URL a running server is reachable at, for handing to the code under test. */
    public static String url(WireMockServer server) {
        return "http://" + HOST + ":" + server.port();
    }
}
