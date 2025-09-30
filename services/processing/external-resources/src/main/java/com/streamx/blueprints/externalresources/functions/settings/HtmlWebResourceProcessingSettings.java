package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.HtmlContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import com.streamx.blueprints.externalresources.services.UrlComputationService;

public class HtmlWebResourceProcessingSettings extends BaseProcessingSettings<WebResource> {

  public HtmlWebResourceProcessingSettings(Configuration configuration,
      UrlComputationService urlComputationService) {
    super(
        new HtmlContentProcessingSettings(configuration),
        new WebResourceMetadata(),
        ".html",
        urlComputationService
    );
  }
}