package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.HtmlContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.WebResourceMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(2)
@ApplicationScoped
public class HtmlWebResourceProcessingSettings extends BaseProcessingSettings<WebResource> {

  @PostConstruct
  void init() {
    loadSettings(
        new HtmlContentProcessingSettings(configuration),
        new WebResourceMetadata(),
        ".html"
    );
  }
}