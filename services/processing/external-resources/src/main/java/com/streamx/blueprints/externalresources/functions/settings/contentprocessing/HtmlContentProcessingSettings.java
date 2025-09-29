package com.streamx.blueprints.externalresources.functions.settings.contentprocessing;

import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.contentadjusters.HtmlContentAdjuster;
import com.streamx.blueprints.externalresources.finders.HtmlValuesFinder;

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
