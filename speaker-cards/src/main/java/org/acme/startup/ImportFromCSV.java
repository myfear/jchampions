package org.acme.startup;

import io.quarkus.runtime.*;
import jakarta.enterprise.context.*;
import jakarta.enterprise.event.*;
import jakarta.inject.*;
import jakarta.transaction.*;
import org.acme.model.*;
import org.acme.model.dto.*;
import org.apache.camel.*;
import org.apache.camel.dataformat.bindy.csv.*;
import org.apache.camel.support.*;
import org.jboss.logging.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

@ApplicationScoped
public class ImportFromCSV
{
  private static final Logger LOG = Logger.getLogger(ImportFromCSV.class);

  @Inject
  CamelContext camelContext;

  // Timezone constants
  private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
  private static final ZoneId CET_ZONE = ZoneId.of("Europe/Paris");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  void onStart(@Observes StartupEvent ev)
  {
    LOG.info(">>> CSV Import startup observer initialized. Import will run when explicitly triggered.");
  }

  @Transactional
  public void importFromCSV(String csvFilePath)
  {
    LOG.infof(">>> Starting CSV import from: %s", csvFilePath);

    Path path = Paths.get(csvFilePath);
    if (!Files.exists(path))
    {
      LOG.errorf("### CSV file not found: %s", csvFilePath);
      return;
    }

    try
    {
      BindyCsvDataFormat bindy = new BindyCsvDataFormat(SpeakerTalkRow.class);

      @SuppressWarnings("unchecked")
      List<SpeakerTalkRow> rows = (List<SpeakerTalkRow>) bindy.unmarshal(
        new DefaultExchange(camelContext),
        Files.newInputStream(path)
      );

      LOG.infof(">>> Parsed %d rows from CSV", rows.size());

      for (SpeakerTalkRow row : rows)
        try
        {
          processRow(row);
        }
        catch (Exception e)
        {
          LOG.errorf(e, "### Error processing row for speaker %s: %s", row.speakerId, e.getMessage());
        }

      LOG.infof(">>> CSV import completed. Processed %d rows", rows.size());
    }
    catch (Exception e)
    {
      LOG.errorf(e, "### Error reading CSV file: %s", csvFilePath);
    }
  }

  private void processRow(SpeakerTalkRow row)
  {
    Talk talk = createOrUpdateTalk(row);
    Speaker speaker = createOrUpdateSpeaker(row);

    if (speaker != null && talk != null)
      if (!talk.speakers.contains(speaker))
      {
        talk.speakers.add(speaker);
        speaker.talks.add(talk);
        talk.persist();
        speaker.persist();
      }
  }

  private Speaker createOrUpdateSpeaker(SpeakerTalkRow row)
  {
    if (row.speakerId == null || row.speakerId.trim().isEmpty())
      return null;

    try
    {
      UUID speakerId = UUID.nameUUIDFromBytes("speaker-%s".formatted(row.speakerId.trim()).getBytes());
      Speaker speaker = Speaker.findById(speakerId);
      boolean isNew = (speaker == null);

      if (isNew)
      {
        speaker = new Speaker();
        speaker.id = speakerId;
      }

      speaker.firstName = emptyToNull(row.firstName);
      speaker.lastName = emptyToNull(row.lastName);
      speaker.title = emptyToNull(row.tagLine);
      speaker.biography = emptyToNull(row.bio);
      speaker.companyURL = emptyToNull(row.companyUrl);
      speaker.twitterAccount = emptyToNull(row.twitterHandle);
      speaker.linkedInAccount = emptyToNull(row.linkedInProfile);
      speaker.githubAccount = emptyToNull(row.githubUsername);
      speaker.blogURL = emptyToNull(row.blogUrl);
      speaker.star = false;

      if (isNew)
        speaker.persist();

      String profilePictureUrl = emptyToNull(row.profilePictureUrl);
      if (profilePictureUrl != null)
        downloadProfilePicture(speakerId, profilePictureUrl);

      LOG.debugf(">>> %s speaker: %s %s (%s)", isNew ? "Persisted" : "Updated",
        speaker.firstName, speaker.lastName, speakerId);
      return speaker;
    }
    catch (Exception e)
    {
      LOG.errorf(e, "### Error creating speaker: %s", e.getMessage());
      return null;
    }
  }

  private Talk createOrUpdateTalk(SpeakerTalkRow row)
  {
    if (row.sessionId == null || row.sessionId.trim().isEmpty())
      return null;

    try
    {
      Long sessionId = Long.parseLong(row.sessionId.trim());
      Talk talk = Talk.findById(sessionId);
      boolean isNew = (talk == null);

      if (isNew)
      {
        if (emptyToNull(row.scheduledAt) != null)
        {
          LOG.warnf("### Skipping new talk with session ID %d: title is empty", sessionId);
          return null;
        }

        talk = new Talk();
        talk.id = sessionId;
        talk.title = row.title.trim();
        talk.description = emptyToNull(row.description);
        talk.scheduledDuration = emptyToNull(row.scheduledDuration);
        talk.liveLink = emptyToNull(row.liveLink);

        if ((emptyToNull(row.scheduledAt) != null))
          try
          {
            LocalDateTime estDateTime = LocalDateTime.parse(row.scheduledAt.trim());
            ZonedDateTime estZoned = estDateTime.atZone(EST_ZONE);
            ZonedDateTime cetZoned = estZoned.withZoneSameInstant(CET_ZONE);
            talk.date = cetZoned.format(DATE_FORMAT);
            talk.estTime = estZoned.format(TIME_FORMAT);
            talk.cetTime = cetZoned.format(TIME_FORMAT);
          }
          catch (Exception e)
          {
            LOG.warnf("Could not parse date '%s' for talk %d", row.scheduledAt, sessionId);
          }

        talk.persist();
        LOG.debugf("Persisted talk: %s (%d)", talk.title, sessionId);
      }
      else
      {
        LOG.debugf("### Found existing talk: %s (%d) with %d speaker(s)", talk.title, sessionId,
          talk.speakers.size());
      }

      return talk;
    }
    catch (Exception e)
    {
      LOG.errorf(e, "### Error creating talk: %s", e.getMessage());
      return null;
    }
  }

  private String emptyToNull(String value)
  {
    return (value == null || value.isBlank()) ? null : value.trim();
  }

  private void downloadProfilePicture(UUID speakerId, String profilePictureUrl)
  {
    if (profilePictureUrl == null || profilePictureUrl.isEmpty()) {
      return;
    }

    try {
      // Determine file extension from URL
      String extension = "jpg"; // default
      int lastDot = profilePictureUrl.lastIndexOf('.');
      if (lastDot > 0) {
        String urlExt = profilePictureUrl.substring(lastDot + 1).toLowerCase();
        // Remove query parameters if any
        int queryIndex = urlExt.indexOf('?');
        if (queryIndex > 0) {
          urlExt = urlExt.substring(0, queryIndex);
        }
        if (urlExt.equals("jpg") || urlExt.equals("jpeg") || urlExt.equals("png") || urlExt.equals("gif")) {
          extension = urlExt;
        }
      }

      // Target path: src/main/resources/META-INF/speaker/{Speaker Id}.{ext}
      Path resourcesPath = Paths.get("src/main/resources/META-INF/speaker");
      Files.createDirectories(resourcesPath);

      Path imagePath = resourcesPath.resolve(speakerId.toString() + "." + extension);

      // Check if file already exists
      if (Files.exists(imagePath)) {
        LOG.debugf("Profile picture already exists for speaker %s, skipping download", speakerId);
        return;
      }

      // Download image
      LOG.infof("Downloading profile picture for speaker %s from %s", speakerId, profilePictureUrl);
      URI uri = URI.create(profilePictureUrl);

      try (InputStream in = uri.toURL().openStream()) {
        Files.copy(in, imagePath);
        LOG.infof("Downloaded profile picture for speaker %s to %s", speakerId, imagePath);
      }

    } catch (Exception e) {
      LOG.errorf(e, "Error downloading profile picture for speaker %s from %s", speakerId, profilePictureUrl);
    }
  }
}
