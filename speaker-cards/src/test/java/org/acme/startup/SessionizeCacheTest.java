package org.acme.startup;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionizeCacheTest {
    @Test
    void sharesColdLoadAndPreservesLastGoodSnapshot(@TempDir Path directory) throws Exception {
        byte[] original = SessionizeScheduleTest.fixture();
        var requests = new AtomicInteger();
        var status = new AtomicInteger(200);
        var body = new AtomicReference<>(original);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/All", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                byte[] response = body.get();
                exchange.sendResponseHeaders(status.get(), response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        var url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/All");
        Path file = directory.resolve("cache/all.json");
        var cache = new SessionizeCache(new ObjectMapper(), url, file);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var loads = new ArrayList<Future<SessionizeCache.Snapshot>>();
            for (int i = 0; i < 12; i++) {
                loads.add(executor.submit(cache::load));
            }
            for (var load : loads) {
                assertSame(cache.load(), load.get());
            }
            assertArrayEquals(original, Files.readAllBytes(file));
            var freshProcess = new SessionizeCache(new ObjectMapper(), url, file);
            assertEquals(cache.load(), freshProcess.load());
            assertEquals(1, requests.get());

            byte[] revised = new String(original, StandardCharsets.UTF_8).replace("Java 27 speaker cards", "Revised talk")
                    .getBytes(StandardCharsets.UTF_8);
            body.set(revised);
            var updated = freshProcess.refresh();
            assertEquals(updated, cache.load());
            assertEquals(2, requests.get());

            for (String invalid : new String[]{"not JSON", "{\"sessions\":[],\"speakers\":[]}"}) {
                body.set(invalid.getBytes(StandardCharsets.UTF_8));
                assertThrows(IllegalArgumentException.class, freshProcess::refresh);
                assertArrayEquals(revised, Files.readAllBytes(file));
                assertSame(updated, freshProcess.load());
            }
            status.set(503);
            assertThrows(SessionizeCache.UpstreamException.class, freshProcess::refresh);
            assertArrayEquals(revised, Files.readAllBytes(file));
            assertSame(updated, freshProcess.load());
            assertEquals(5, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
