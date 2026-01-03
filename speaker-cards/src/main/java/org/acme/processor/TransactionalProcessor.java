package org.acme.processor;

import jakarta.enterprise.context.*;
import jakarta.transaction.*;
import org.apache.camel.*;

@ApplicationScoped
public class TransactionalProcessor implements Processor
{
  @Override
  @Transactional
  public void process(Exchange exchange) throws Exception
  {
  }
}
