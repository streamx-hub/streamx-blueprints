package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.JsonContentProcessingSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(2)
@ApplicationScoped
public class JsonWebResourceProcessingSettings extends BaseWebResourceProcessingSettings {

  @PostConstruct
  void init() {
    loadSettings(
        new JsonContentProcessingSettings(configuration),
        ".json"
    );
  }
}