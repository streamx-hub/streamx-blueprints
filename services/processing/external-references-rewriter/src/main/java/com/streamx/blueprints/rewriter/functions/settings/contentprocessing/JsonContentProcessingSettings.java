package com.streamx.blueprints.rewriter.functions.settings.contentprocessing;

import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.contentadjusters.JsonContentAdjuster;
import com.streamx.blueprints.rewriter.finders.JsonValuesFinder;

public class JsonContentProcessingSettings extends BaseContentProcessingSettings {

  public JsonContentProcessingSettings(Configuration configuration) {
    super(
        new JsonValuesFinder(),
        new JsonContentAdjuster(),
        configuration.jsonExternalResourceJsonpathSelectors(),
        configuration.jsonExternalResourceUrlExclusionsPattern()
    );
  }
}
