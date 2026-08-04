package com.otilm.core.config.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guards the request-factory defaults every platform {@code RestClient} inherits. A bare
 * {@code ClientHttpRequestFactoryBuilder.detect()} fails each of these.
 */
class PlatformHttpClientsTest {

    private static final int CONCURRENT_REQUESTS = 10;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String url;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile List<String> lastCookieHeader;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverExecutor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS * 2);
        server.setExecutor(serverExecutor);
        url = "http://localhost:" + server.getAddress().getPort() + "/probe";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        // HttpServer.stop() leaves an executor supplied via setExecutor running.
        serverExecutor.shutdownNow();
    }

    private RestClient client() {
        return RestClient.builder().requestFactory(PlatformHttpClients.requestFactoryBuilder().build()).build();
    }

    private void respond(HttpExchange exchange, int status, String cookie) throws IOException {
        requestCount.incrementAndGet();
        lastCookieHeader = exchange.getRequestHeaders().get("Cookie");
        if (cookie != null) {
            exchange.getResponseHeaders().add("Set-Cookie", cookie);
        }
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Test
    void testPoolAllowsMoreConcurrencyThanApacheDefault() throws Exception {
        CountDownLatch arrived = new CountDownLatch(CONCURRENT_REQUESTS);
        server.createContext("/probe", exchange -> {
            arrived.countDown();
            try {
                arrived.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, null);
        });
        server.start();

        RestClient client = client();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                calls.add(executor.submit(() -> client.get().uri(url).retrieve().toBodilessEntity()));
            }

            Assertions.assertTrue(arrived.await(5, TimeUnit.SECONDS),
                    "connection pool held concurrency below " + CONCURRENT_REQUESTS + " simultaneous requests");
            for (Future<?> call : calls) {
                Assertions.assertNotNull(call.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testDoesNotReplayCookies() {
        server.createContext("/probe", exchange -> respond(exchange, 200, "SESSION=first-caller; Path=/"));
        server.start();

        RestClient client = client();
        client.get().uri(url).retrieve().toBodilessEntity();
        client.get().uri(url).retrieve().toBodilessEntity();

        Assertions.assertEquals(2, requestCount.get());
        Assertions.assertNull(lastCookieHeader, "second request carried a cookie from the first response");
    }

    @Test
    void testDoesNotRetryOnServiceUnavailable() {
        server.createContext("/probe", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "30");
            respond(exchange, 503, null);
        });
        server.start();

        RestClient client = client();
        Assertions.assertThrows(HttpServerErrorException.class,
                () -> client.get().uri(url).retrieve().toBodilessEntity());

        Assertions.assertEquals(1, requestCount.get());
    }

    @Test
    void testRetriesOnceAfterConnectionFailure() {
        server.createContext("/probe", exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                exchange.close();
                return;
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        Assertions.assertEquals(200, client().get().uri(url).retrieve().toBodilessEntity().getStatusCode().value());
        Assertions.assertEquals(2, requestCount.get());
    }
}
