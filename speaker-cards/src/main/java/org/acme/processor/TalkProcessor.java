package org.acme.processor;

import jakarta.enterprise.context.*;
import jakarta.transaction.*;
import org.acme.model.*;
import org.acme.model.dto.*;
import org.apache.camel.*;
import org.apache.commons.lang3.*;

import java.time.*;
import java.time.format.*;

@ApplicationScoped
public class TalkProcessor implements Processor
{
  @Override
  @Transactional
  public void process(Exchange exchange) throws Exception
  {
    SpeakerTalkRow row = exchange.getIn().getBody(SpeakerTalkRow.class);
    Talk talk = Talk.fromRow(row);
    if (talk != null)
      talk.persist();
  }
}
