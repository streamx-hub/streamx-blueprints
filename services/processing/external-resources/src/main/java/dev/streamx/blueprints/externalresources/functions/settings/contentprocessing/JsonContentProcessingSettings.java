package dev.streamx.blueprints.externalresources.functions.settings.contentprocessing;

import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.JsonContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.JsonValuesFinder;

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
