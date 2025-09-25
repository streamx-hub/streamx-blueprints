package dev.streamx.blueprints.externalresources.functions.settings.contentprocessing;

import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.XmlContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.XmlValuesFinder;

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
