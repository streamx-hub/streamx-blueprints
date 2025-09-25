package dev.streamx.blueprints.externalresources.functions.settings;

import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import dev.streamx.blueprints.externalresources.functions.settings.contentprocessing.BaseContentProcessingSettings;
import dev.streamx.blueprints.externalresources.functions.settings.resourcemetadata.BaseResourceMetadata;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import java.util.Set;
import java.util.function.BiFunction;

public abstract class BaseProcessingSettings<T extends Resource> {

  private final BaseResourceContentAdjuster contentAdjuster;
  private final ExternalResourcesCollector externalResourcesCollector;
  private final Class<T> handledResourceClass;
  private final BiFunction<String, String, T> newResourceConstructor;
  private final Set<String> handledCloudEventTypes;
  private final String handledResourcePathSuffix;

  protected BaseProcessingSettings(
      BaseContentProcessingSettings contentProcessingSettings,
      BaseResourceMetadata<T> resourceMetadata,
      String handledResourcePathSuffix,
      UrlComputationService urlComputationService) {
    this.contentAdjuster = contentProcessingSettings.getContentAdjuster();
    this.externalResourcesCollector = new ExternalResourcesCollector(
        contentProcessingSettings, urlComputationService);
    this.handledResourceClass = resourceMetadata.getResourceClass();
    this.newResourceConstructor = resourceMetadata.getResourceConstructor();
    this.handledCloudEventTypes = resourceMetadata.getEventTypes();
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
    return newResourceConstructor.apply(content, payloadType);
  }
}