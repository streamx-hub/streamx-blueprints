package com.streamx.blueprints.rewriter.functions.settings;

import com.streamx.blueprints.rewriter.functions.settings.contentprocessing.YamlContentProcessingSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(2)
@ApplicationScoped
public class YamlWebResourceProcessingSettings extends BaseWebResourceProcessingSettings {

  @PostConstruct
  void init() {
    loadSettings(
        new YamlContentProcessingSettings(configuration),
        ".yaml"
    );
  }
}