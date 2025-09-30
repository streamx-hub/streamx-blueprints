package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.XmlContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import com.streamx.blueprints.externalresources.services.UrlComputationService;

public class XmlWebResourceProcessingSettings extends BaseProcessingSettings<WebResource> {

  public XmlWebResourceProcessingSettings(Configuration configuration,
      UrlComputationService urlComputationService) {
    super(
        new XmlContentProcessingSettings(configuration),
        new WebResourceMetadata(),
        ".xml",
        urlComputationService
    );
  }

}