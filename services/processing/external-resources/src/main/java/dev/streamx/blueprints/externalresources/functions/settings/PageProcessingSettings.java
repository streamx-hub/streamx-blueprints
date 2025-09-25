package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.functions.settings.contentprocessing.HtmlContentProcessingSettings;
import dev.streamx.blueprints.externalresources.functions.settings.resourcemetadata.PageMetadata;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;

public class PageProcessingSettings extends BaseProcessingSettings<Page> {

  public PageProcessingSettings(Configuration configuration,
      UrlComputationService urlComputationService) {
    super(
        new HtmlContentProcessingSettings(configuration),
        new PageMetadata(),
        "",
        urlComputationService
    );
  }
}