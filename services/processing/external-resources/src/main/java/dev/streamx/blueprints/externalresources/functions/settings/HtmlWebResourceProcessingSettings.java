package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.functions.settings.contentprocessing.HtmlContentProcessingSettings;
import dev.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;

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