package com.streamx.blueprints.rewriter.functions.settings.contentprocessing;

import static com.streamx.blueprints.rewriter.configuration.Configuration.yamlExternalResourceJsonpathSelectors;
import static com.streamx.blueprints.rewriter.configuration.Configuration.yamlExternalResourceUrlExclusionsPattern;

import com.streamx.blueprints.rewriter.contentadjusters.JsonContentAdjuster;
import com.streamx.blueprints.rewriter.finders.YamlValuesFinder;

public class YamlContentProcessingSettings extends BaseContentProcessingSettings {

  public YamlContentProcessingSettings() {
    super(
        new YamlValuesFinder(),
        new JsonContentAdjuster(),
        yamlExternalResourceJsonpathSelectors(),
        yamlExternalResourceUrlExclusionsPattern()
    );
  }
}
