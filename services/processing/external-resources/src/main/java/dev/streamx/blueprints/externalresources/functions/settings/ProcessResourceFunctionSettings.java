package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import dev.streamx.blueprints.externalresources.finders.BaseValuesFinder;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

public abstract class ProcessResourceFunctionSettings<T extends Resource> {

  private final BaseResourceContentAdjuster contentAdjuster;
  private final ExternalResourcesCollector externalResourcesCollector;
  private final Class<T> handledResourceClass;
  private final BiFunction<String, String, T> newResourceFunction;
  private final Set<String> handledCloudEventTypes;
  private final String handledResourcePathSuffix;

  public ProcessResourceFunctionSettings(
      BaseValuesFinder valuesFinder,
      BaseResourceContentAdjuster contentAdjuster,
      Class<T> handledResourceClass,
      BiFunction<String, String, T> newResourceFunction,
      Set<String> handledCloudEventTypes,
      String handledResourcePathSuffix,
      Logger log,
      UrlComputationService urlComputationService,
      Optional<List<String>> resourceSelectors,
      Optional<Pattern> resourceUrlExclusionsPattern) {
    this.contentAdjuster = contentAdjuster;
    this.externalResourcesCollector = new ExternalResourcesCollector(
        log, urlComputationService, valuesFinder, resourceSelectors, resourceUrlExclusionsPattern);
    this.handledResourceClass = handledResourceClass;
    this.newResourceFunction = newResourceFunction;
    this.handledCloudEventTypes = handledCloudEventTypes;
    this.handledResourcePathSuffix = handledResourcePathSuffix;
  }

  public BaseResourceContentAdjuster getContentAdjuster() {
    return contentAdjuster;
  }

  public ExternalResourcesCollector getExternalResourcesCollector() {
    return externalResourcesCollector;
  }

  public Class<? extends Resource> getHandledResourceClass() {
    return handledResourceClass;
  }

  public Set<String> getHandledCloudEventTypes() {
    return Set.copyOf(handledCloudEventTypes);
  }

  public String getHandledResourcePathSuffix() {
    return handledResourcePathSuffix;
  }

  public T newResource(String content, String payloadType) {
    return newResourceFunction.apply(content, payloadType);
  }
}