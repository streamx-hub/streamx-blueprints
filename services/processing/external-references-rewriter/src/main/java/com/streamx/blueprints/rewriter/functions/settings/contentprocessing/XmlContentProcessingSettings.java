package com.streamx.blueprints.rewriter.functions.settings.contentprocessing;

import static com.streamx.blueprints.rewriter.configuration.Configuration.xmlExternalResourceUrlExclusionsPattern;
import static com.streamx.blueprints.rewriter.configuration.Configuration.xmlExternalResourceXpathSelectors;

import com.streamx.blueprints.rewriter.contentadjusters.XmlContentAdjuster;
import com.streamx.blueprints.rewriter.finders.XmlValuesFinder;

public class XmlContentProcessingSettings extends BaseContentProcessingSettings {

  public XmlContentProcessingSettings() {
    super(
        new XmlValuesFinder(),
        new XmlContentAdjuster(),
        xmlExternalResourceXpathSelectors(),
        xmlExternalResourceUrlExclusionsPattern()
    );
  }
}
