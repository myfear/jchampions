package org.acme.model;

import java.sql.Types;
import java.time.*;
import java.time.format.*;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import org.acme.model.dto.*;
import org.apache.commons.lang3.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class Talk extends PanacheEntityBase implements Comparable<Talk>
{
  @Id
  public Long id;

  public String title;

  // At least one description must be filled
  @JdbcTypeCode(Types.LONGVARCHAR)
  @Length(max = 10000)
  public String description;

  public String date;
  public String estTime;
  public String cetTime;
  public String scheduledDuration;
  @URL
  public String liveLink;

  private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
  private static final ZoneId CET_ZONE = ZoneId.of("Europe/Paris");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");


  public Talk()
  {
  }

  public Talk(Long id, String title, String description, String scheduledDuration, String liveLink)
  {
    this.id = id;
    this.title = title;
    this.description = description;
    this.scheduledDuration = scheduledDuration;
    this.liveLink = liveLink;
  }

  public Talk(Talk talk, String date, String estTime, String cetTime)
  {
    this(talk.id, talk.title, talk.description, talk.scheduledDuration, talk.liveLink);
    this.date = date;
    this.estTime = estTime;
    this.cetTime = cetTime;
  }

  @JoinTable(name = "talk_speaker", joinColumns = @JoinColumn(name = "talk_id"), inverseJoinColumns = @JoinColumn(name = "speakers_id"))
  @ManyToMany
  public List<Speaker> speakers = new ArrayList<Speaker>();

  public int compareTo(Talk other)
  {
    return this.title.compareTo(other.title);
  }

  public static Talk fromRow(SpeakerTalkRow row)
  {
    if (StringUtils.isBlank(row.sessionId))
    {
      return null;
    }

    Long sessionId = Long.parseLong(row.sessionId.trim());

    Talk talk = new Talk(sessionId, StringUtils.trimToNull(row.title),
      StringUtils.trimToNull(row.description),
      StringUtils.trimToNull(row.scheduledDuration),
        StringUtils.trimToNull(row.liveLink));

    // Parse scheduled time
    if (StringUtils.isNotBlank(row.scheduledAt))
    {
      try
      {
        LocalDateTime estDateTime = LocalDateTime.parse(row.scheduledAt.trim());
        ZonedDateTime estZoned = estDateTime.atZone(EST_ZONE);
        ZonedDateTime cetZoned = estZoned.withZoneSameInstant(CET_ZONE);
        talk = new Talk(talk, estZoned.format(DATE_FORMAT), estZoned.format(TIME_FORMAT),
          cetZoned.format(TIME_FORMAT));
      }
      catch (Exception e)
      {
        // Log warning but continue
      }
    }

    return talk;
  }
}