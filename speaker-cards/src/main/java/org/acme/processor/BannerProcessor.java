package org.acme.processor;

import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.transaction.*;
import org.acme.service.*;
import org.apache.camel.*;
import org.apache.commons.lang3.*;
import org.jboss.logging.*;

@ApplicationScoped
public class BannerProcessor implements Processor
{

  private static final Logger LOG = Logger.getLogger(BannerProcessor.class);

  @Inject
  BannerGenerationService bannerService;

  @Override
  @Transactional
  public void process(Exchange exchange) throws Exception
  {
    LOG.info(">>> Starting banner generation after CSV import");
    String outputDir = exchange.getIn().getHeader("outputDir", String.class);
    BannerGenerationResult result = !StringUtils.isBlank(outputDir) ?
      bannerService.generateAllBanners(outputDir.trim()) :
      bannerService.generateAllSpeakerBanners();
    LOG.infof(">>> Banner generation completed: %d successful, %d failed",
      result.getSuccessCount(), result.getFailureCount());
    exchange.getIn().setBody(result);
  }
}
