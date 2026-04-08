package com.streamx.blueprints.rewriter.functions.settings.contentprocessing;

import static com.streamx.blueprints.rewriter.configuration.Configuration.htmlExternalResourceUrlExclusionsPattern;
import static com.streamx.blueprints.rewriter.configuration.Configuration.htmlExternalResourceXpathSelectors;

import com.streamx.blueprints.rewriter.contentadjusters.HtmlContentAdjuster;
import com.streamx.blueprints.rewriter.finders.HtmlValuesFinder;

public class HtmlContentProcessingSettings extends BaseContentProcessingSettings {

  public HtmlContentProcessingSettings() {
    super(
        new HtmlValuesFinder(),
        new HtmlContentAdjuster(),
        htmlExternalResourceXpathSelectors(),
        htmlExternalResourceUrlExclusionsPattern()
    );
  }
}
