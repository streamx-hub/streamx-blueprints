package dev.streamx.blueprints.externalresources.contentadjusters;

import dev.streamx.blueprints.externalresources.data.ExternalResource;
import java.util.Set;
import org.jboss.logging.Logger;

class BaseContentAdjusterTest {

  private static final Logger log = Logger.getLogger(BaseContentAdjusterTest.class);

  private final BaseResourceContentAdjuster contentAdjuster;

  protected BaseContentAdjusterTest(BaseResourceContentAdjuster contentAdjuster) {
    this.contentAdjuster = contentAdjuster;
  }

  protected String adjustLinks(String inputContent, Set<ExternalResource> externalResources) {
    return contentAdjuster.adjustLinks(inputContent, externalResources, log);
  }
}