package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.HtmlContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.PageMetadata;
import com.streamx.blueprints.externalresources.services.UrlComputationService;

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