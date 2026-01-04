package org.acme.processor;

import jakarta.enterprise.context.*;
import org.apache.camel.*;
import org.apache.commons.lang3.*;

import java.util.*;

@ApplicationScoped
public class PhotoProcessor implements Processor
{
  @Override
  public void process(Exchange exchange)
  {
    String url = exchange.getIn().getHeader("photoUrl", String.class);
    String extension = StringUtils.substringAfterLast(url, ".").toLowerCase();
    if (extension.contains("?"))
      extension = StringUtils.substringBefore(extension, "?");
    if (!List.of("jpg", "jpeg", "png", "gif").contains(extension))
      extension = "jpg";
    UUID speakerId = UUID.nameUUIDFromBytes(("speaker-%s"
      .formatted(exchange.getIn()
        .getHeader("speakerId")).getBytes()));
    exchange.getIn().setHeader("fileName", "%s.%s".formatted(speakerId, extension));
    exchange.getIn().setBody(null);
  }
}
