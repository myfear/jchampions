package org.acme.model.dto;

import org.apache.camel.dataformat.bindy.annotation.*;

@CsvRecord(separator = ",", skipFirstLine = true)
public class SpeakerTalkRow
{
  @DataField(pos = 1, required = true)
  public String sessionId;
  @DataField(pos = 2, required = true)
  public String title;
  @DataField(pos = 3, required = true)
  public String description;
  @DataField(pos = 4, required = true)
  public String companyUrl;
  @DataField(pos = 5)  // Empty column
  public String unused5;
  @DataField(pos = 6)  // Empty column
  public String unused6;
  @DataField(pos = 7)  // Empty column
  public String unused7;
  @DataField(pos = 8)  // Empty column
  public String unused8;
  @DataField(pos = 9, required = true)
  public String scheduledAt;
  @DataField(pos = 10, required = true)
  public String scheduledDuration;
  @DataField(pos = 11, required = true)
  public String liveLink;
  @DataField(pos = 12)  // Empty column
  public String unused12;
  @DataField(pos = 13, required = true)
  public String speakerId;
  @DataField(pos = 14, required = true)
  public String firstName;
  @DataField(pos = 15, required = true)
  public String lastName;
  @DataField(pos = 16)  // Empty column
  public String unused16;
  @DataField(pos = 17, required = true)
  public String tagLine;
  @DataField(pos = 18, required = true)
  public String bio;
  @DataField(pos = 19, required = true)
  public String twitterHandle;
  @DataField(pos = 20)
  public String linkedInProfile;
  @DataField(pos = 21)
  public String githubUsername;
  @DataField(pos = 22)
  public String blogUrl;
  @DataField(pos = 23)
  public String profilePictureUrl;

  @Override
  public String toString()
  {
    return "SpeakerTalkRow{speakerId=%s, firstName=%s, lastName=%s"
      .formatted(speakerId, firstName, lastName);
  }
}
