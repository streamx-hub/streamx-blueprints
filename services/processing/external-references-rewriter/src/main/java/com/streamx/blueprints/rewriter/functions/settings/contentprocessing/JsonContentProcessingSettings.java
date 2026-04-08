package com.streamx.blueprints.rewriter.functions.settings.contentprocessing;

import static com.streamx.blueprints.rewriter.configuration.Configuration.jsonExternalResourceJsonpathSelectors;
import static com.streamx.blueprints.rewriter.configuration.Configuration.jsonExternalResourceUrlExclusionsPattern;

import com.streamx.blueprints.rewriter.contentadjusters.JsonContentAdjuster;
import com.streamx.blueprints.rewriter.finders.JsonValuesFinder;

public class JsonContentProcessingSettings extends BaseContentProcessingSettings {

  public JsonContentProcessingSettings() {
    super(
        new JsonValuesFinder(),
        new JsonContentAdjuster(),
        jsonExternalResourceJsonpathSelectors(),
        jsonExternalResourceUrlExclusionsPattern()
    );
  }
}
