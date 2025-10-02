package com.streamx.blueprints.dependenciesrewriter.functions.settings.contentprocessing;

import com.streamx.blueprints.dependenciesrewriter.configuration.Configuration;
import com.streamx.blueprints.dependenciesrewriter.contentadjusters.HtmlContentAdjuster;
import com.streamx.blueprints.dependenciesrewriter.finders.HtmlValuesFinder;

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
