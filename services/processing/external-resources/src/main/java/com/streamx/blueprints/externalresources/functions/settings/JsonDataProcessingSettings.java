package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.JsonContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.DataMetadata;
import com.streamx.blueprints.externalresources.services.UrlComputationService;

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