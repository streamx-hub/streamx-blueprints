package com.streamx.blueprints.dependenciesrewriter.functions.settings.contentprocessing;

import com.streamx.blueprints.dependenciesrewriter.configuration.Configuration;
import com.streamx.blueprints.dependenciesrewriter.contentadjusters.JsonContentAdjuster;
import com.streamx.blueprints.dependenciesrewriter.finders.JsonValuesFinder;

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
