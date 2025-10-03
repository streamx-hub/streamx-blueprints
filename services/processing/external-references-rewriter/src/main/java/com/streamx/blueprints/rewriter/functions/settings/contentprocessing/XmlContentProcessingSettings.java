package com.streamx.blueprints.rewriter.functions.settings.contentprocessing;

import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.contentadjusters.XmlContentAdjuster;
import com.streamx.blueprints.rewriter.finders.XmlValuesFinder;

public class XmlContentProcessingSettings extends BaseContentProcessingSettings {

  public XmlContentProcessingSettings(Configuration configuration) {
    super(
        new XmlValuesFinder(),
        new XmlContentAdjuster(),
        configuration.xmlExternalResourceXpathSelectors(),
        configuration.xmlExternalResourceUrlExclusionsPattern()
    );
  }
}
