package com.streamx.blueprints.externalresources.functions.settings.contentprocessing;

import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.contentadjusters.JsonContentAdjuster;
import com.streamx.blueprints.externalresources.finders.JsonValuesFinder;

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
