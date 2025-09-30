package com.streamx.blueprints.externalresources.functions.settings.contentprocessing;

import com.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import com.streamx.blueprints.externalresources.finders.BaseValuesFinder;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public abstract class BaseContentProcessingSettings {

  private final BaseValuesFinder valuesFinder;
  private final BaseResourceContentAdjuster contentAdjuster;
  private final Optional<List<String>> resourceSelectors;
  private final Optional<Pattern> resourceUrlExclusionsPattern;

  BaseContentProcessingSettings(
      BaseValuesFinder valuesFinder,
      BaseResourceContentAdjuster contentAdjuster,
      Optional<List<String>> resourceSelectors,
      Optional<Pattern> resourceUrlExclusionsPattern) {
    this.valuesFinder = valuesFinder;
    this.contentAdjuster = contentAdjuster;
    this.resourceSelectors = resourceSelectors;
    this.resourceUrlExclusionsPattern = resourceUrlExclusionsPattern;
  }

  public BaseValuesFinder getValuesFinder() {
    return valuesFinder;
  }

  public BaseResourceContentAdjuster getContentAdjuster() {
    return contentAdjuster;
  }

  public Optional<List<String>> getResourceSelectors() {
    return resourceSelectors;
  }

  public Optional<Pattern> getResourceUrlExclusionsPattern() {
    return resourceUrlExclusionsPattern;
  }
}
