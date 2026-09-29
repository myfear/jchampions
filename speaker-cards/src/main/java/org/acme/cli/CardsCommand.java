package org.acme.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.acme.service.BannerGenerationResult;
import org.acme.service.BannerGenerationService;
import org.acme.startup.ImportFromCSV;
import org.acme.startup.SessionizeSchedule;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(name = "cards", description = "Generate jChampions speaker cards from Sessionize exports",
        mixinStandardHelpOptions = true,
        subcommands = {CardsCommand.Validate.class, CardsCommand.Generate.class, CardsCommand.Preview.class})
public class CardsCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }

    private static SessionizeSchedule.Summary inspect(SessionizeSchedule schedules, Path schedule) throws IOException {
        SessionizeSchedule.Summary summary = schedules.inspect(schedule);
        System.out.printf("Valid Sessionize export: %d rows, %d speakers, %d talks, %d cards%n",
                summary.rows(), summary.speakers(), summary.talks(), summary.cards());
        System.out.println("Schedule years: " + summary.years());
        return summary;
    }

    @CommandLine.Command(name = "validate", description = "Check an XLSX export without importing", mixinStandardHelpOptions = true)
    public static class Validate implements Callable<Integer> {
        @CommandLine.Parameters(index = "0", description = "Sessionize XLSX export")
        Path schedule;

        @Inject
        SessionizeSchedule schedules;

        @ConfigProperty(name = "conference.year")
        int year;

        @Override
        public Integer call() {
            try {
                SessionizeSchedule.Summary summary = inspect(schedules, schedule);
                if (!summary.years().equals(Set.of(year))) {
                    System.err.printf("Warning: schedule years %s differ from card branding year %d%n", summary.years(), year);
                }
                return 0;
            } catch (Exception e) {
                System.err.println("cards: " + e.getMessage());
                return 1;
            }
        }
    }

    @CommandLine.Command(name = "generate", description = "Import an XLSX export and write every card", mixinStandardHelpOptions = true)
    public static class Generate implements Callable<Integer> {
        @CommandLine.Parameters(index = "0", description = "Sessionize XLSX export")
        Path schedule;

        @CommandLine.Option(names = "--output", description = "Output directory (default: speaker-banners-YEAR)")
        Path output;

        @CommandLine.Option(names = "--allow-year-mismatch", description = "Allow a test run with dates from another year")
        boolean allowYearMismatch;

        @Inject
        SessionizeSchedule schedules;

        @Inject
        ImportFromCSV importer;

        @Inject
        BannerGenerationService banners;

        @ConfigProperty(name = "conference.year")
        int year;

        @Override
        public Integer call() {
            try {
                SessionizeSchedule.Summary summary = inspect(schedules, schedule);
                if (!allowYearMismatch && !summary.years().equals(Set.of(year))) {
                    throw new IllegalArgumentException("Schedule years differ from " + year
                            + ". Use --allow-year-mismatch for a test run");
                }
                Path destination = output == null ? Path.of("speaker-banners-" + year) : output;
                importer.importFromCSV(schedule.toAbsolutePath().toString());
                BannerGenerationResult result = banners.generateAllBanners(destination.toString());
                Set<String> distinctFiles = new HashSet<>(result.getSavedFiles());
                System.out.printf("Generated %d cards in %s%n", result.getSuccessCount(), destination.toAbsolutePath());
                if (result.hasFailures() || result.getSuccessCount() != summary.cards()
                        || distinctFiles.size() != summary.cards()) {
                    for (BannerGenerationResult.FailureEntry failure : result.getFailures()) {
                        System.err.printf("Failed: %s: %s%n", failure.speakerName, failure.errorMessage);
                    }
                    throw new IllegalStateException("Expected " + summary.cards() + " distinct cards; got "
                            + distinctFiles.size() + " files and " + result.getFailureCount() + " render failures");
                }
                return 0;
            } catch (Exception e) {
                System.err.println("cards: " + e.getMessage());
                return 1;
            }
        }
    }

    @CommandLine.Command(name = "preview", description = "Import an XLSX export and save one PNG", mixinStandardHelpOptions = true)
    public static class Preview implements Callable<Integer> {
        @CommandLine.Parameters(index = "0", description = "Sessionize XLSX export")
        Path schedule;

        @CommandLine.Parameters(index = "1", description = "speaker, talk, or social")
        Kind kind;

        @CommandLine.Parameters(index = "2", description = "Speaker UUID or talk ID")
        String id;

        @CommandLine.Option(names = "--output", required = true, description = "PNG destination")
        Path output;

        @CommandLine.Option(names = "--allow-year-mismatch", description = "Allow a test run with dates from another year")
        boolean allowYearMismatch;

        @Inject
        SessionizeSchedule schedules;

        @Inject
        ImportFromCSV importer;

        @ConfigProperty(name = "conference.year")
        int year;

        @ConfigProperty(name = "quarkus.http.port")
        int port;

        enum Kind { speaker, talk, social }

        @Override
        public Integer call() {
            try {
                SessionizeSchedule.Summary summary = inspect(schedules, schedule);
                if (!allowYearMismatch && !summary.years().equals(Set.of(year))) {
                    throw new IllegalArgumentException("Schedule years differ from " + year
                            + ". Use --allow-year-mismatch for a test run");
                }
                String endpoint = switch (kind) {
                    case talk -> "/talk-banner/" + Long.parseLong(id) + ".png";
                    case speaker -> "/speaker-banner/" + UUID.fromString(id) + ".png";
                    case social -> "/speaker-social/" + UUID.fromString(id) + ".png";
                };
                importer.importFromCSV(schedule.toAbsolutePath().toString());
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + endpoint))
                        .timeout(Duration.ofSeconds(30)).GET().build();
                byte[] png;
                try (HttpClient client = HttpClient.newHttpClient()) {
                    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("PNG endpoint returned HTTP " + response.statusCode());
                    }
                    png = response.body();
                }
                if (png.length < 8 || png[0] != (byte) 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
                    throw new IllegalStateException("PNG endpoint did not return a PNG");
                }
                Path parent = output.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(output, png);
                System.out.println("Saved " + output.toAbsolutePath());
                return 0;
            } catch (Exception e) {
                System.err.println("cards: " + e.getMessage());
                return 1;
            }
        }
    }
}
