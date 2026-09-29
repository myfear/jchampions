package org.acme.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
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
import org.acme.startup.ImportFromCSV;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class SpeakerCardsTest {

    @Inject
    ImportFromCSV importer;

    @TestHTTPResource
    URI baseUri;

    @TempDir
    Path temporaryDirectory;

    @Test
    void importsExcelScheduleAndRendersAllCardFormats() throws Exception {
        assertEquals("Poppins", new java.awt.Font("Poppins", java.awt.Font.BOLD, 12)
                .getFamily(java.util.Locale.ROOT), "PNG rendering must use the bundled font");
        UUID speakerId = UUID.randomUUID();
        Path schedule = temporaryDirectory.resolve("schedule.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Schedule");
            sheet.createRow(0).createCell(0).setCellValue("Session Id");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("987654321");
            row.createCell(1).setCellValue("Java 27 speaker cards");
            row.createCell(2).setCellValue("A rendering smoke test.");
            row.createCell(8).setCellValue("2027-01-22T10:00:00");
            row.createCell(9).setCellValue("60");
            row.createCell(12).setCellValue(speakerId.toString());
            row.createCell(13).setCellValue("Test");
            row.createCell(14).setCellValue("Speaker");
            row.createCell(16).setCellValue("Java developer");
            row.createCell(17).setCellValue("Speaker biography for the rendering test.");
            try (var output = Files.newOutputStream(schedule)) {
                workbook.write(output);
            }
        }

        importer.importFromCSV(schedule.toString());
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                Speaker speaker = Speaker.findById(speakerId);
                assertNotNull(speaker);
                assertEquals("Test Speaker", speaker.toString());
                assertEquals(1, speaker.talks.size());
                Talk talk = speaker.talks.getFirst();
                assertEquals("Java 27 speaker cards", talk.title);
                assertEquals("10:00", talk.estTime);
                assertEquals("16:00", talk.cetTime);
                assertEquals("2027-01-22", talk.date);
            });

            try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
                var directory = client.send(HttpRequest.newBuilder(baseUri)
                        .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, directory.statusCode());
                assertTrue(directory.body().contains("Test Speaker"));

                var card = client.send(HttpRequest.newBuilder(baseUri.resolve("/speaker-banner/" + speakerId))
                        .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, card.statusCode());
                assertTrue(card.body().contains("22 January 2027"));
                assertTrue(card.body().contains("class=\"year\">2027"));

                assertPng(client, "/speaker-banner/" + speakerId + ".png", 1280, 720);
                assertPng(client, "/talk-banner/987654321.png", 1280, 720);
                assertPng(client, "/speaker-social/" + speakerId + ".png", 1080, 1080);
            }
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                Talk.deleteById(987654321L);
                Speaker.deleteById(speakerId);
            });
        }
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
