package com.streamx.blueprints.rewriter.functions.settings;

import com.streamx.blueprints.rewriter.functions.settings.contentprocessing.HtmlContentProcessingSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(2)
@ApplicationScoped
public class HtmlWebResourceProcessingSettings extends BaseWebResourceProcessingSettings {

  @PostConstruct
  void init() {
    loadSettings(
        new HtmlContentProcessingSettings(),
        ".html"
    );
  }
}