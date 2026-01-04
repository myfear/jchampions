package org.acme.router;

import jakarta.enterprise.context.*;
import jakarta.inject.*;
import org.acme.model.dto.*;
import org.acme.processor.*;
import org.apache.camel.*;
import org.apache.camel.builder.*;
import org.apache.camel.dataformat.bindy.csv.*;

@ApplicationScoped
public class CsvImportRouter extends RouteBuilder
{
  @Inject
  SpeakerProcessor speakerProcessor;
  @Inject
  TalkProcessor talkProcessor;
  @Inject
  BannerProcessor bannerProcessor;
  @Inject
  PhotoProcessor photoProcessor;


  @Override
  public void configure()
  {
    BindyCsvDataFormat bindy = new BindyCsvDataFormat(SpeakerTalkRow.class);

    from("file:./csv-input?include=SelectedWithSchedule.csv&move=.done&moveFailed=.error&idempotent=true&idempotentKey=${file:name}")
      .routeId("csv-import")
      .onException(Exception.class)
        .handled(true)
        .log(LoggingLevel.ERROR, ">>> CSV import failed: ${exception.message}")
        .stop()
      .end()
      .log(">>> Starting CSV import from ${header.CamelFileName}")
      .unmarshal(bindy)
      .split(body())
        .to("direct:process-speaker")
        .to("direct:process-talk")
        .to("direct:download-photo")
      .end()
      .log(">>> CSV import completed")
      .setHeader("outputDir", constant("./speaker-banners"))
      .to("direct:generate-banners");

    from("direct:process-speaker")
      .process(speakerProcessor);

    from("direct:process-talk")
      .process(talkProcessor);

    from("direct:download-photo")
      .filter(simple("${body.profilePictureUrl} != null"))
      .setHeader("speakerId", simple("${body.speakerId}"))
      .setHeader("photoUrl", simple("${body.profilePictureUrl}"))
      .process(photoProcessor)      .toD("${header.photoUrl}?httpMethod=GET")
      .toD("file:./speaker-photos?fileName=${header.fileName}&fileExist=Ignore")
      .log(">>> Downloaded photo for speaker ${header.speakerId}");

    from("direct:generate-banners")
      .process(bannerProcessor)
      .log(">>> Banner generation pipeline completed");
  }
}
