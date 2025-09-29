package com.streamx.blueprints.externalresources.functions.settings.contentprocessing;

import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.contentadjusters.XmlContentAdjuster;
import com.streamx.blueprints.externalresources.finders.XmlValuesFinder;

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
