package org.acme.model;

import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.acme.model.dto.*;
import org.apache.commons.lang3.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Speaker extends PanacheEntityBase implements Comparable<Speaker>
{
  @Id
  public UUID id;

  public String firstName;
  @NotBlank
  public String lastName;
  public String title;
  @JdbcTypeCode(Types.LONGVARCHAR)
  @NotBlank
  @Length(max = 10000)
  public String biography;
  public String company;
  @URL
  public String companyURL;
  @URL
  public String blogURL;
  public String twitterAccount;
  public String linkedInAccount;
  public String githubAccount;

  public String email;

  public String importId;

  public boolean star;

  public Speaker()
  {
  }

  public Speaker(UUID id, String firstName, String lastName, String title,
                 String biography, String companyURL, String twitterAccount,
                 String linkedInAccount, String githubAccount, String blogURL) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.title = title;
    this.biography = biography;
    this.companyURL = companyURL;
    this.twitterAccount = twitterAccount;
    this.linkedInAccount = linkedInAccount;
    this.githubAccount = githubAccount;
    this.blogURL = blogURL;
    this.star = false;
  }

  @ManyToMany(mappedBy = "speakers")
  public List<Talk> talks = new ArrayList<Talk>();

  public String phone;

  public Date lastUpdated;

  @PreUpdate
  @PrePersist
  public void prePersist()
  {
    lastUpdated = Date.from(Instant.now());
  }

  @Override
  public String toString()
  {
    return firstName + " " + lastName;
  }

  @Override
  public int compareTo(Speaker o)
  {
    return toString().compareTo(o.toString());
  }

  public String getTalksForTwitter()
  {
    StringBuilder talksTwitter = new StringBuilder();
    for (Talk talk : talks)
    {
      talksTwitter.append("🎙️«").append(talk.title).append("»\n");
    }
    return talksTwitter.toString();
  }

  public static Speaker fromRow(SpeakerTalkRow row)
  {
    UUID speakerId = UUID.nameUUIDFromBytes("speaker-%s".formatted(row.speakerId.trim()).getBytes());
    Speaker speaker = new Speaker(
      speakerId,
      StringUtils.trimToNull(row.firstName),
      StringUtils.trimToNull(row.lastName),
      StringUtils.trimToNull(row.tagLine),
      StringUtils.trimToNull(row.bio),
      StringUtils.trimToNull(row.companyUrl),
      StringUtils.trimToNull(row.twitterHandle),
      StringUtils.trimToNull(row.linkedInProfile),
      StringUtils.trimToNull(row.githubUsername),
      StringUtils.trimToNull(row.blogUrl)
    );
    return speaker;
  }
}