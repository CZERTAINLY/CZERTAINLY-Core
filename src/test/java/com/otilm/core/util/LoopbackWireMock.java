package com.otilm.core.util;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extension;

/**
 * Starts WireMock stubs on an OS-chosen port bound to the IPv4 loopback address.
 * <p>
 * The explicit bind address is the point: the OS grants a wildcard bind a port another process already holds on
 * {@code 127.0.0.1}, and SO_REUSEADDR lets it succeed, leaving that process to answer the stub's requests.
 */
public final class LoopbackWireMock {

    /** The IPv4 loopback address, used literally so a caller cannot resolve to {@code ::1} and miss the server. */
    public static final String HOST = "127.0.0.1";

    private LoopbackWireMock() {
    }

    /** Extensions must arrive here: WireMock registers response transformers only at server creation. */
    public static WireMockServer start(Extension... extensions) {
        WireMockServer server = new WireMockServer(
                WireMockConfiguration.options().bindAddress(HOST).dynamicPort().extensions(extensions));
        server.start();
        return server;
    }

    public static String url(WireMockServer server) {
        return "http://" + HOST + ":" + server.port();
    }
}
