package org.acme.router;

import io.quarkus.runtime.*;
import jakarta.enterprise.context.*;
import jakarta.enterprise.event.*;
import jakarta.inject.*;
import org.acme.model.dto.*;
import org.acme.processor.*;
import org.acme.startup.*;
import org.apache.camel.*;
import org.apache.camel.builder.*;
import org.apache.camel.dataformat.bindy.csv.*;
import org.jboss.logging.*;

import java.util.*;

@ApplicationScoped
public class CsvImportRouter extends RouteBuilder
{
  private static final Logger LOG = Logger.getLogger(CsvImportRouter.class);

  @Inject
  SpeakerProcessor speakerProcessor;
  @Inject
  TalkProcessor talkProcessor;
  @Inject
  BannerProcessor bannerProcessor;

  @Override
  public void configure() throws Exception
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
      .to("direct:generate-banners");

    from("direct:process-speaker")
      .process(speakerProcessor);

    from("direct:process-talk")
      .process(talkProcessor);

    from("direct:download-photo")
      .filter(simple("${body.profilePictureUrl} != null"))
      .setHeader("speakerId", simple("${body.speakerId}"))
      .setHeader("photoUrl", simple("${body.profilePictureUrl}"))
      .process(exchange ->
      {
        String url = exchange.getIn().getHeader("photoUrl", String.class);
        String extension = url.substring(url.lastIndexOf('.') + 1).toLowerCase();
        if (extension.contains("?")) extension = extension.substring(0, extension.indexOf("?"));
        if (!List.of("jpg", "jpeg", "png", "gif").contains(extension)) extension = "jpg";

        UUID speakerId = UUID.nameUUIDFromBytes(("speaker-" + exchange.getIn().getHeader("speakerId")).getBytes());
        String fileName = speakerId + "." + extension;
        exchange.getIn().setHeader("fileName", fileName);
        exchange.getIn().setBody(null);
      })
      .toD("${header.photoUrl}?httpMethod=GET")
      .toD("file:src/main/resources/META-INF/speaker?fileName=${header.fileName}&fileExist=Ignore")
      .log(">>> Downloaded photo for speaker ${header.speakerId}");

    from("direct:generate-banners")
      .process(bannerProcessor)
      .log(">>> Banner generation pipeline completed");
  }
}
