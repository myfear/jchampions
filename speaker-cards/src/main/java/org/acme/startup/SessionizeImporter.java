package org.acme.startup;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.acme.model.Speaker;
import org.acme.model.Talk;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SessionizeImporter {
    private static final Logger LOG = Logger.getLogger(SessionizeImporter.class);

    @ConfigProperty(name = "speaker.photo-directory", defaultValue = "src/main/resources/META-INF/speaker")
    Path photoDirectory;

    @Transactional
    public void importSchedule(SessionizeSchedule schedule) {
        Map<UUID, Speaker> speakers = new HashMap<>();
        schedule.speakers().forEach((id, person) -> {
            Speaker speaker = Speaker.findById(id);
            if (speaker == null) {
                speaker = new Speaker();
                speaker.id = id;
            }
            speaker.firstName = person.firstName();
            speaker.lastName = person.lastName();
            speaker.title = person.tagLine();
            speaker.biography = person.bio();
            speaker.persist();
            speakers.put(id, speaker);
            downloadMissingPhoto(id, person.profilePicture());
        });
        for (var session : schedule.sessions()) {
            Talk talk = Talk.findById(session.id());
            if (talk == null) {
                talk = new Talk();
                talk.id = session.id();
            }
            talk.title = session.title();
            talk.description = session.description();
            talk.date = Objects.toString(session.date(), null);
            talk.estTime = session.estTime();
            talk.cetTime = session.cetTime();
            talk.scheduledDuration = Objects.toString(session.minutes(), null);
            talk.liveLink = session.liveUrl();
            for (Speaker previous : talk.speakers) {
                previous.talks.remove(talk);
            }
            talk.speakers.clear();
            for (UUID id : session.speakers()) {
                Speaker speaker = speakers.get(id);
                talk.speakers.add(speaker);
                speaker.talks.add(talk);
            }
            talk.persist();
        }
    }

    private void downloadMissingPhoto(UUID id, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return;
            }
            String path = uri.getPath().toLowerCase(Locale.ROOT);
            for (String ext : List.of(".jpg", ".jpeg", ".png")) {
                if (Files.isRegularFile(photoDirectory.resolve(id + ext))) {
                    return;
                }
            }
            Path target = photoDirectory.resolve(id + (path.endsWith(".png") ? ".png" : ".jpg"));
            Files.createDirectories(photoDirectory);
            var connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            Path temp = Files.createTempFile(photoDirectory, ".photo-", ".tmp");
            try {
                try (var input = connection.getInputStream()) {
                    Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                connection.disconnect();
                Files.deleteIfExists(temp);
            }
        } catch (IOException | IllegalArgumentException e) {
            LOG.warnf("Could not download photo for %s: %s", id, e.getMessage());
        }
    }
}
