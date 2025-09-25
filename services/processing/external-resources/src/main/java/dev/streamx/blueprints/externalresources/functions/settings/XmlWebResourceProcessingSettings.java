package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.functions.settings.contentprocessing.XmlContentProcessingSettings;
import dev.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;

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