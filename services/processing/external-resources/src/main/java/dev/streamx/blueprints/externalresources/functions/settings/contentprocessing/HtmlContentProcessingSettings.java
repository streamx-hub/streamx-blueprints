package dev.streamx.blueprints.externalresources.functions.settings.contentprocessing;

import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.HtmlContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.HtmlValuesFinder;

public class HtmlContentProcessingSettings extends BaseContentProcessingSettings {

  public HtmlContentProcessingSettings(Configuration configuration) {
    super(
        new HtmlValuesFinder(),
        new HtmlContentAdjuster(),
        configuration.htmlExternalResourceXpathSelectors(),
        configuration.htmlExternalResourceUrlExclusionsPattern()
    );
  }
}
