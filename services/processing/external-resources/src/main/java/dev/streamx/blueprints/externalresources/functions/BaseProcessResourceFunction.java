package dev.streamx.blueprints.externalresources.functions;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.contentadjusters.BaseResourceContentAdjuster;
import dev.streamx.blueprints.externalresources.data.ExternalResource;
import dev.streamx.blueprints.externalresources.data.ParentResource;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;

abstract class BaseProcessResourceFunction<T extends Resource> {

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  UrlComputationService urlComputationService;

  @Inject
  ExternalResourcesProcessFunction externalResourcesProcessFunction;

  private Class<T> handledResourceType;
  private ExternalResourcesCollector externalResourcesCollector;

  protected abstract ExternalResourcesCollector externalResourcesCollector();

  protected abstract BaseResourceContentAdjuster contentAdjuster();

  protected abstract T newResource(String content);

  @PostConstruct
  @SuppressWarnings("unchecked")
  void init() {
    handledResourceType = (Class<T>) newResource("any").getClass();
    externalResourcesCollector = externalResourcesCollector();
  }

  protected Uni<Message<T>> processIncomingResource(Message<T> resourceMessage) {
    if (shouldSkipProcessingMessage(resourceMessage)) {
      return asRelayedMessage(resourceMessage);
    }

    String resourcePath = extractKey(resourceMessage);
    String resourceContent = Optional.ofNullable(resourceMessage.getPayload())
        .map(Resource::getContentAsString)
        .orElse(null);
    ParentResource<T> resource = toParentResource(resourcePath, resourceContent);

    log.infof("Resource %s, having absolute url %s, will be published to StreamX as %s",
        resourcePath, resource.getAbsoluteUrl(), resource.getStreamxKey());

    Action action = extractAction(resourceMessage);
    if (!Action.PUBLISH.equals(action)) {
      // TODO implement unpublishing orphaned external resources, for now just relay the message
      // TODO implement it also for publishing an edited resource with some links removed
      return asProcessedMessage(resourceMessage, resource.getStreamxKey());
    }

    Set<ExternalResource> externalResources = externalResourcesCollector
        .collectExternalResources(resource);

    if (externalResources.isEmpty()) {
      log.tracef("No external resources found for %s", resource.getStreamxKey());
      return asProcessedMessage(resourceMessage, resource.getStreamxKey());
    }

    log.tracef("Found %d external resources for %s: %s",
        externalResources.size(),
        resource.getStreamxKey(),
        externalResources.stream().map(ExternalResource::getPaths).collect(Collectors.toList())
    );

    return downloadExternalResourcesAndReturnAdjustedResource(
        resourceMessage, resource, externalResources
    );
  }

  private boolean shouldSkipProcessingMessage(Message<T> message) {
    String path = extractKey(message);
    Properties properties = message.getMetadata(Properties.class).orElseGet(Properties::empty);

    if (!externalResourcesCollector.hasResourceSelectors()) {
      log.tracef("Skipping processing resource %s - no resource selectors are specified", path);
      return true;
    }

    Optional<String> sxTypeOpt = properties.getType();
    if (sxTypeOpt.isEmpty()) {
      log.tracef("Skipping processing resource %s - sx:type property is missing", path);
      return true;
    }

    String sxType = sxTypeOpt.get();
    if (!configuration.processableSxTypes().contains(sxType)) {
      log.tracef("Skipping processing resource %s - not matching sx:type: %s", path, sxType);
      return true;
    }

    return false;
  }

  private Uni<Message<T>> asRelayedMessage(Message<T> message) {
    message.ack();
    return Uni.createFrom().item(message);
  }

  private Uni<Message<T>> asProcessedMessage(Message<T> message, String key) {
    Metadata adjustedMetadata = message.getMetadata().with(Key.of(key));
    Message<T> adjustedMessage = Message.of(message.getPayload(), adjustedMetadata);
    message.ack();
    return Uni.createFrom().item(adjustedMessage);
  }

  private ParentResource<T> toParentResource(String path, String content) {
    if (WebResource.class.isAssignableFrom(handledResourceType)) {
      // web resources later become available by http as files, so must sanitize their paths
      String sanitizedResourcePath = urlComputationService.asStreamxKey(path);
      String resourceAbsoluteUrl = urlComputationService
          .computeAbsoluteUrlRelativeToConfiguredBaseUrl(sanitizedResourcePath);
      String resourceStreamxKey = urlComputationService
          .asStreamxKeyRelativeToConfiguredBaseUrl(resourceAbsoluteUrl);
      return new ParentResource<>(
          resourceAbsoluteUrl, resourceStreamxKey, content, handledResourceType);
    } else {
      return new ParentResource<>(
          configuration.baseUrlForRelativePaths(), path, content, handledResourceType);
    }
  }

  private Uni<Message<T>> downloadExternalResourcesAndReturnAdjustedResource(
      Message<T> resourceMessage, ParentResource<T> parentResource,
      Set<ExternalResource> externalResources) {

    return Multi.createFrom().iterable(externalResources)
        .onItem()
        .transformToUniAndMerge(resource -> externalResourcesProcessFunction
            .downloadAndPublish(resource, parentResource))
        .collect().last()
        .map(ignored -> {
          String content = parentResource.getContent();

          log.tracef("Replacing external links in %s", parentResource.getStreamxKey());
          String adjustedContent = contentAdjuster().adjustLinks(content, externalResources, log);

          Metadata adjustedMetadata = resourceMessage.getMetadata()
              .with(Key.of(parentResource.getStreamxKey()))
              .with(EventTime.of(System.currentTimeMillis()));

          log.tracef("Publishing resource %s with all external links adjusted to local paths",
              parentResource.getStreamxKey());

          resourceMessage.ack();
          T adjustedResource = newResource(adjustedContent);
          return Message.of(adjustedResource, adjustedMetadata);
        });
  }
}