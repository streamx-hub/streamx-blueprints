package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.JsonContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(2)
@ApplicationScoped
public class JsonWebResourceProcessingSettings extends BaseProcessingSettings<WebResource> {

  @PostConstruct
  void init() {
    loadSettings(
        new JsonContentProcessingSettings(configuration),
        new WebResourceMetadata(),
        ".json"
    );
  }
}