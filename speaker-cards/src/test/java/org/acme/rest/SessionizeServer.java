package org.acme.rest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class SessionizeServer implements QuarkusTestResourceLifecycleManager {
    private HttpServer server;
    private Path directory;

    @Override
    public Map<String, String> start() {
        try {
            directory = Files.createTempDirectory("speaker-cards-test");
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            Path upstream = directory.resolve("upstream.json");
            server.createContext("/All", exchange -> {
                try (exchange) {
                    if (!Files.exists(upstream)) {
                        exchange.sendResponseHeaders(503, -1);
                        return;
                    }
                    byte[] bytes = Files.readAllBytes(upstream);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.createContext("/portrait.png", exchange -> {
                try (exchange; var image = getClass().getResourceAsStream("/META-INF/resources/static/images/duke_cool.png")) {
                    byte[] bytes = image.readAllBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
            return Map.of("sessionize.url", "http://127.0.0.1:" + server.getAddress().getPort() + "/All",
                    "sessionize.cache-file", directory.resolve("cache.json").toString(),
                    "speaker.photo-directory", directory.resolve("photos").toString(),
                    "test.sessionize-upstream", upstream.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void stop() {
        server.stop(0);
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
