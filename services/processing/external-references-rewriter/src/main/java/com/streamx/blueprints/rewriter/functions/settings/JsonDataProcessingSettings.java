package com.streamx.blueprints.rewriter.functions.settings;

import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.rewriter.functions.settings.contentprocessing.JsonContentProcessingSettings;
import com.streamx.blueprints.rewriter.functions.settings.resourcemetadata.DataMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(1)
@ApplicationScoped
public class JsonDataProcessingSettings extends BaseProcessingSettings<Data> {

  @PostConstruct
  void init() {
    loadSettings(
        new JsonContentProcessingSettings(configuration),
        new DataMetadata()
    );
  }
}