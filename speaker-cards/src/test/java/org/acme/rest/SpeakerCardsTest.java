package org.acme.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.acme.model.Speaker;
import org.acme.model.Talk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(SessionizeServer.class)
class SpeakerCardsTest {

    @Inject
    ObjectMapper mapper;

    @TestHTTPResource
    URI baseUri;

    @ConfigProperty(name = "test.sessionize-upstream")
    Path upstream;

    @ConfigProperty(name = "speaker.photo-directory")
    Path photos;

    @ConfigProperty(name = "sessionize.url")
    URI sessionizeUrl;

    private String csrfToken;

    @Test
    void importsCachedScheduleAndRendersAllCardFormats() throws Exception {
        assertEquals("Poppins", new java.awt.Font("Poppins", java.awt.Font.BOLD, 12)
                .getFamily(java.util.Locale.ROOT), "PNG rendering must use the bundled font");
        UUID speakerId = UUID.fromString("28b89d1f-8695-4eb8-b255-c2a90737fe88");
        UUID guestId = UUID.fromString("7099698a-644c-4ed5-a636-fced3538cb95");
        Path photo = photos.resolve(speakerId + ".png");
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
             var input = getClass().getResourceAsStream("/sessionize-all.json")) {
            var home = client.send(HttpRequest.newBuilder(baseUri).GET().build(), HttpResponse.BodyHandlers.discarding());
            csrfToken = home.headers().allValues("Set-Cookie").stream()
                    .flatMap(header -> HttpCookie.parse(header).stream())
                    .filter(cookie -> cookie.getName().equals("csrf-token")).findFirst().orElseThrow().getValue();
            assertEquals(502, post(client, "import").statusCode());
            Files.writeString(upstream, "{}");
            assertEquals(422, post(client, "refresh").statusCode());
            var fixture = mapper.readTree(input);
            ((ObjectNode) fixture.at("/speakers/0")).put("profilePicture", sessionizeUrl.resolve("/portrait.png").toString());
            var slot = (ObjectNode) fixture.at("/sessions/0");
            slot.put("status", "Accepted");
            slot.putNull("startsAt");
            slot.putNull("endsAt");
            Files.write(upstream, mapper.writeValueAsBytes(fixture));
            var refreshed = post(client, "refresh");
            assertEquals(200, refreshed.statusCode(), refreshed.body());
            var metadata = mapper.readTree(refreshed.body());
            assertEquals(5, metadata.at("/summary/cards").intValue());
            assertTrue(metadata.hasNonNull("timestamp"));
            assertTrue(Files.exists(Path.of(metadata.get("path").asText())));
            QuarkusTransaction.requiringNew().run(() -> assertEquals(0L, Talk.count()));
            Files.delete(upstream);
            var imported = post(client, "import");
            assertEquals(200, imported.statusCode(), imported.body());
            QuarkusTransaction.requiringNew().run(() -> {
                Speaker speaker = Speaker.findById(speakerId);
                assertNotNull(speaker);
                assertEquals("Test Speaker", speaker.toString());
                assertEquals(1, speaker.talks.size());
                Talk talk = speaker.talks.getFirst();
                assertEquals("Java 27 speaker cards", talk.title);
                assertNull(talk.estTime);
                assertNull(talk.cetTime);
                assertNull(talk.date);
                assertNull(talk.scheduledDuration);
            });

            byte[] expectedPhoto;
            try (var image = getClass().getResourceAsStream("/META-INF/resources/static/images/duke_cool.png")) {
                expectedPhoto = image.readAllBytes();
            }
            assertArrayEquals(expectedPhoto, Files.readAllBytes(photo));
            var photoResponse = client.send(HttpRequest.newBuilder(baseUri.resolve("/speaker-photo/" + speakerId))
                    .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, photoResponse.statusCode());
            assertArrayEquals(expectedPhoto, photoResponse.body());

            var directory = client.send(HttpRequest.newBuilder(baseUri)
                    .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, directory.statusCode());
            assertTrue(directory.body().contains("Test Speaker"));
            assertTrue(directory.body().contains("download=\"Speaker_Test.png\""));
            assertTrue(directory.body().contains("download=\"Speaker_Speaker_java-27-speaker-cards.png\""));

            var card = client.send(HttpRequest.newBuilder(baseUri.resolve("/speaker-banner/" + speakerId))
                    .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, card.statusCode());
            assertFalse(card.body().contains("class=\"session-date\""));
            assertFalse(card.body().contains("class=\"session-time\""));
            assertTrue(card.body().contains("class=\"year\">2027"));

            assertPng(client, "/speaker-banner/" + speakerId + ".png", 1280, 720);
            assertPng(client, "/talk-banner/987654321.png", 1280, 720);
            assertPng(client, "/speaker-social/" + speakerId + ".png", 1080, 1080);

            slot.put("title", "Updated title");
            slot.put("startsAt", "2027-01-23T11:15:00Z");
            slot.put("endsAt", "2027-01-23T12:00:00Z");
            slot.putArray("speakers").add(guestId.toString());
            ((ObjectNode) fixture.at("/speakers/1")).put("bio", "Updated biography");
            Files.write(upstream, mapper.writeValueAsBytes(fixture));
            assertEquals(200, post(client, "refresh").statusCode());
            assertEquals(200, post(client, "import").statusCode());
            assertEquals(200, post(client, "import").statusCode());
            QuarkusTransaction.requiringNew().run(() -> {
                Talk updated = Talk.findById(987654321L);
                assertEquals("Updated title", updated.title);
                assertEquals("2027-01-23", updated.date);
                assertEquals("12:15", updated.cetTime);
                assertEquals("45", updated.scheduledDuration);
                assertEquals(1, updated.speakers.size());
                assertEquals(guestId, updated.speakers.getFirst().id);
                assertEquals("Updated biography", updated.speakers.getFirst().biography);
                assertTrue(Speaker.<Speaker>findById(speakerId).talks.isEmpty());
            });
        } finally {
            Files.deleteIfExists(photo);
            QuarkusTransaction.requiringNew().run(() -> {
                Talk.deleteById(987654321L);
                Speaker.deleteById(speakerId);
                Speaker.deleteById(guestId);
            });
        }
    }

    private HttpResponse<String> post(HttpClient client, String action) throws Exception {
        var request = HttpRequest.newBuilder(baseUri.resolve("/api/sessionize/" + action))
                .header("Cookie", "csrf-token=" + csrfToken).header("X-CSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(30)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertPng(HttpClient client, String path, int width, int height) throws Exception {
        var response = client.send(HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), path);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("image/png"), path);
        var image = ImageIO.read(new ByteArrayInputStream(response.body()));
        assertNotNull(image, path);
        assertEquals(width, image.getWidth(), path);
        assertEquals(height, image.getHeight(), path);
    }
}
