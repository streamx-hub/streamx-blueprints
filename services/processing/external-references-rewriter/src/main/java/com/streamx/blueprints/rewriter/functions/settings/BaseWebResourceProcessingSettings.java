package com.streamx.blueprints.rewriter.functions.settings;

import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.rewriter.functions.settings.contentprocessing.BaseContentProcessingSettings;
import com.streamx.blueprints.rewriter.functions.settings.resourcemetadata.WebResourceMetadata;

public abstract class BaseWebResourceProcessingSettings extends
    BaseProcessingSettings<WebResource> {

  private String handledResourcePathSuffix;

  protected void loadSettings(BaseContentProcessingSettings contentProcessingSettings,
      String handledResourcePathSuffix) {
    super.loadSettings(contentProcessingSettings, new WebResourceMetadata());
    this.handledResourcePathSuffix = handledResourcePathSuffix;
  }

  @Override
  public boolean handlesResourcePath(String path) {
    return path.endsWith(handledResourcePathSuffix);
  }
}