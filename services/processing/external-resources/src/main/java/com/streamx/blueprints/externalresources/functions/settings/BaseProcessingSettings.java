package com.streamx.blueprints.externalresources.functions.settings;

import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.BaseContentProcessingSettings;
import com.streamx.blueprints.externalresources.functions.settings.resourcemetadata.BaseResourceMetadata;
import com.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import com.streamx.blueprints.externalresources.services.UrlComputationService;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.function.BiFunction;

public abstract class BaseProcessingSettings<T extends Resource> {

  @Inject
  Configuration configuration;

  @Inject
  UrlComputationService urlComputationService;

  private BaseResourceContentAdjuster contentAdjuster;
  private ExternalResourcesCollector externalResourcesCollector;
  private Class<T> handledResourceClass;
  private BiFunction<String, String, T> newResourceConstructor;
  private Set<String> handledCloudEventTypes;

  protected void loadSettings(
      BaseContentProcessingSettings contentProcessingSettings,
      BaseResourceMetadata<T> resourceMetadata) {
    this.contentAdjuster = contentProcessingSettings.getContentAdjuster();
    this.externalResourcesCollector = new ExternalResourcesCollector(
        contentProcessingSettings, urlComputationService);
    this.handledResourceClass = resourceMetadata.getResourceClass();
    this.newResourceConstructor = resourceMetadata.getResourceConstructor();
    this.handledCloudEventTypes = resourceMetadata.getEventTypes();
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

  public boolean handledCloudEventType(String eventType) {
    return handledCloudEventTypes.contains(eventType);
  }

  public boolean handlesResourcePath(String path) {
    return true;
  }

  public T newResource(String content, String payloadType) {
    return newResourceConstructor.apply(content, payloadType);
  }
}