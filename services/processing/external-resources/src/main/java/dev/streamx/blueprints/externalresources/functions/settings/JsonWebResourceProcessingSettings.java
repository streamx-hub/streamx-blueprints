package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.functions.settings.contentprocessing.JsonContentProcessingSettings;
import dev.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;

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