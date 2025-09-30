package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.JsonContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import com.streamx.blueprints.externalresources.services.UrlComputationService;

public class JsonWebResourceProcessingSettings extends BaseProcessingSettings<WebResource> {

  public JsonWebResourceProcessingSettings(Configuration configuration,
      UrlComputationService urlComputationService) {
    super(
        new JsonContentProcessingSettings(configuration),
        new WebResourceMetadata(),
        ".json",
        urlComputationService
    );
  }
}