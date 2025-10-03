package com.streamx.blueprints.rewriter.contentadjusters;

import com.streamx.blueprints.rewriter.data.ExternalResource;
import java.util.Set;

class BaseContentAdjusterTest {

  private final BaseResourceContentAdjuster contentAdjuster;

  protected BaseContentAdjusterTest(BaseResourceContentAdjuster contentAdjuster) {
    this.contentAdjuster = contentAdjuster;
  }

  protected String adjustLinks(String inputContent, Set<ExternalResource> externalResources) {
    return contentAdjuster.adjustLinks(inputContent, externalResources);
  }
}