package com.streamx.blueprints.rewriter.functions.settings.contentprocessing;

import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.contentadjusters.JsonContentAdjuster;
import com.streamx.blueprints.rewriter.finders.YamlValuesFinder;

public class YamlContentProcessingSettings extends BaseContentProcessingSettings {

  public YamlContentProcessingSettings(Configuration configuration) {
    super(
        new YamlValuesFinder(),
        new JsonContentAdjuster(),
        configuration.yamlExternalResourceJsonpathSelectors(),
        configuration.yamlExternalResourceUrlExclusionsPattern()
    );
  }
}
