package dev.streamx.blueprints.externalresources.functions.settings.resourcemetadata;

import dev.streamx.blueprints.data.Resource;
import java.util.Set;
import java.util.function.BiFunction;

public abstract class BaseResourceMetadata<T extends Resource> {

  private final Class<T> resourceClass;
  private final BiFunction<String, String, T> resourceConstructor;
  private final String publishedEventType;
  private final String unpublishedEventType;

  BaseResourceMetadata(Class<T> resourceClass, BiFunction<String, String, T> resourceConstructor,
      String publishedEventType, String unpublishedEventType) {
    this.resourceClass = resourceClass;
    this.resourceConstructor = resourceConstructor;
    this.publishedEventType = publishedEventType;
    this.unpublishedEventType = unpublishedEventType;
  }

  public Class<T> getResourceClass() {
    return resourceClass;
  }

  public BiFunction<String, String, T> getResourceConstructor() {
    return resourceConstructor;
  }

  public Set<String> getEventTypes() {
    return Set.of(publishedEventType, unpublishedEventType);
  }
}
