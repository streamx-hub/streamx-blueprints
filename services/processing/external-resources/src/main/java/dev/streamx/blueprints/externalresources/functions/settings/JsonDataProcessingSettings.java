package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.functions.settings.contentprocessing.JsonContentProcessingSettings;
import dev.streamx.blueprints.externalresources.functions.settings.resourcemetadata.DataMetadata;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;

public class JsonDataProcessingSettings extends BaseProcessingSettings<Data> {

  public JsonDataProcessingSettings(Configuration configuration,
      UrlComputationService urlComputationService) {
    super(
        new JsonContentProcessingSettings(configuration),
        new DataMetadata(),
        "",
        urlComputationService
    );
  }
}