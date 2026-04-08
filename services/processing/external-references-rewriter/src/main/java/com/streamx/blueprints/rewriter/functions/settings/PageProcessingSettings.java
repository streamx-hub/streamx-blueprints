package com.streamx.blueprints.rewriter.functions.settings;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.rewriter.functions.settings.contentprocessing.HtmlContentProcessingSettings;
import com.streamx.blueprints.rewriter.functions.settings.resourcemetadata.PageMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(1)
@ApplicationScoped
public class PageProcessingSettings extends BaseProcessingSettings<Page> {

  @PostConstruct
  void init() {
    loadSettings(
        new HtmlContentProcessingSettings(),
        new PageMetadata()
    );
  }
}