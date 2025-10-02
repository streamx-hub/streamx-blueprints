package com.streamx.blueprints.dependenciesrewriter.functions.settings.contentprocessing;

import com.streamx.blueprints.dependenciesrewriter.configuration.Configuration;
import com.streamx.blueprints.dependenciesrewriter.contentadjusters.XmlContentAdjuster;
import com.streamx.blueprints.dependenciesrewriter.finders.XmlValuesFinder;

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
