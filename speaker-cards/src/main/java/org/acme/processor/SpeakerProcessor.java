package org.acme.processor;

import jakarta.enterprise.context.*;
import jakarta.enterprise.context.control.*;
import jakarta.transaction.*;
import org.acme.model.*;
import org.acme.model.dto.*;
import org.apache.camel.*;
import org.apache.commons.lang3.*;

import java.util.*;

@ApplicationScoped
public class SpeakerProcessor implements Processor
{
  @Override
  @Transactional
  public void process(Exchange exchange) throws Exception
  {
    SpeakerTalkRow row = exchange.getIn().getBody(SpeakerTalkRow.class);
    Speaker speaker = Speaker.fromRow(row);
    if (speaker != null)
      speaker.persist();
  }
}
