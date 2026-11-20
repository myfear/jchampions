package org.acme.startup;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SessionizeCache {
    private final ObjectMapper mapper;
    private final URI url;
    private final Path path;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private Snapshot current;
    private Object fileKey;

    @Inject
    public SessionizeCache(ObjectMapper mapper, @ConfigProperty(name = "sessionize.url") URI url,
                          @ConfigProperty(name = "sessionize.cache-file") Path path) {
        this.mapper = mapper;
        this.url = url;
        this.path = path.toAbsolutePath().normalize();
    }

    public record Snapshot(SessionizeSchedule schedule, String path, Instant timestamp) {}

    public synchronized Snapshot load() throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            return refresh();
        }
        // An explicit refresh from a separate CLI process replaces the file.
        if (current == null || !Objects.equals(fileKey, attributes.fileKey())
                || !current.timestamp().equals(attributes.lastModifiedTime().toInstant())) {
            remember(SessionizeSchedule.parse(mapper, Files.readAllBytes(path)), attributes);
        }
        return current;
    }

    public synchronized Snapshot refresh() throws IOException {
        byte[] body = fetch();
        SessionizeSchedule schedule = SessionizeSchedule.parse(mapper, body);
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".sessionize-", ".tmp");
        try {
            Files.write(temp, body);
            var attributes = Files.readAttributes(temp, BasicFileAttributes.class);
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return remember(schedule, attributes);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Snapshot remember(SessionizeSchedule schedule, BasicFileAttributes attributes) {
        fileKey = attributes.fileKey();
        current = new Snapshot(schedule, path.toString(), attributes.lastModifiedTime().toInstant());
        return current;
    }

    public static class UpstreamException extends IOException {
        UpstreamException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private byte[] fetch() throws UpstreamException {
        var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30)).header("Accept", "application/json").GET().build();
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("Sessionize request interrupted", e);
        } catch (IOException e) {
            throw new UpstreamException("Could not reach Sessionize at " + url, e);
        }
        if (response.statusCode() != 200) {
            throw new UpstreamException("Sessionize returned HTTP " + response.statusCode(), null);
        }
        return response.body();
    }
}
