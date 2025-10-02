package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.XmlContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(2)
@ApplicationScoped
public class XmlWebResourceProcessingSettings extends BaseProcessingSettings<WebResource> {

  @PostConstruct
  void init() {
    loadSettings(
        new XmlContentProcessingSettings(configuration),
        new WebResourceMetadata(),
        ".xml"
    );
  }

}