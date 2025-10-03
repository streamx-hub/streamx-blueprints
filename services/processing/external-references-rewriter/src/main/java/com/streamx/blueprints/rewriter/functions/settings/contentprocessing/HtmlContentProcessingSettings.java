package com.streamx.blueprints.rewriter.functions.settings.contentprocessing;

import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.contentadjusters.HtmlContentAdjuster;
import com.streamx.blueprints.rewriter.finders.HtmlValuesFinder;

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
