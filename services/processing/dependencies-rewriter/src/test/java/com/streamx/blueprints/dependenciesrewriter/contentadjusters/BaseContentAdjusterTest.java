package com.streamx.blueprints.dependenciesrewriter.contentadjusters;

import com.streamx.blueprints.dependenciesrewriter.data.ExternalResource;
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