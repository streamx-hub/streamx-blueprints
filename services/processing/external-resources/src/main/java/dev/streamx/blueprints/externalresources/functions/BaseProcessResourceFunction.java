package dev.streamx.blueprints.externalresources.functions;

import static java.util.Objects.requireNonNull;

import dev.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.Channels;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.data.ExternalResource;
import dev.streamx.blueprints.externalresources.data.ParentResource;
import dev.streamx.blueprints.externalresources.functions.settings.ProcessHtmlWebResourceFunctionSettings;
import dev.streamx.blueprints.externalresources.functions.settings.ProcessJsonDataFunctionSettings;
import dev.streamx.blueprints.externalresources.functions.settings.ProcessJsonWebResourceFunctionSettings;
import dev.streamx.blueprints.externalresources.functions.settings.ProcessPageFunctionSettings;
import dev.streamx.blueprints.externalresources.functions.settings.ProcessResourceFunctionSettings;
import dev.streamx.blueprints.externalresources.functions.settings.ProcessXmlWebResourceFunctionSettings;
import dev.streamx.blueprints.externalresources.services.UrlComputationService;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
// TODO remove "Base"
public class BaseProcessResourceFunction {

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  UrlComputationService urlComputationService;

  @Inject
  ExternalResourcesProcessFunction externalResourcesProcessFunction;

  private final List<ProcessResourceFunctionSettings<?>> processingSettings = new ArrayList<>();

  @PostConstruct
  void init() {
    Stream.of(
            new ProcessPageFunctionSettings(log, urlComputationService, configuration),
            new ProcessJsonDataFunctionSettings(log, urlComputationService, configuration),
            new ProcessXmlWebResourceFunctionSettings(log, urlComputationService, configuration),
            new ProcessJsonWebResourceFunctionSettings(log, urlComputationService, configuration),
            new ProcessHtmlWebResourceFunctionSettings(log, urlComputationService, configuration)
        ).sorted(Comparator.comparing(settings ->
            // give priority to settings that define a path suffix (false is "less than" true)
            settings.getHandledResourcePathSuffix().isEmpty()))
        .forEach(processingSettings::add);
  }

  @Incoming(Channels.INCOMING_RESOURCES)
  @Outgoing(Channels.OUTGOING_RESOURCES)
  public Uni<CloudEvent> processIncomingEvent(CloudEvent event) {
    String resourcePath = requireNonNull(event.getSubject());
    Resource payload = CloudEventUtils.getData(event, Resource.class);
    if (payload == null) {
      log.tracef("Skipping processing %s - payload is null - cannot determine payload type", resourcePath);
      return asRelayedEvent(event);
    }

    String payloadType = payload.getType();
    if (!configuration.processablePayloadTypes().contains(payloadType)) {
      log.tracef("Skipping processing %s - the service is not configured to handle payload type %s", resourcePath, payloadType);
      return asRelayedEvent(event);
    }

    String eventType = event.getType();
    var settingsOpt = processingSettings.stream()
        .filter(setting -> setting.getHandledCloudEventTypes().contains(eventType))
        .filter(setting -> resourcePath.endsWith(setting.getHandledResourcePathSuffix()))
        .findFirst();
    if (settingsOpt.isEmpty()) {
      log.tracef("No handler for resource event %s of type %s", resourcePath, eventType);
      return asRelayedEvent(event);
    }

    ProcessResourceFunctionSettings<?> settings = settingsOpt.get();
    if (!settings.getExternalResourcesCollector().hasResourceSelectors()) {
      log.tracef("Skipping processing %s - no resource selectors are specified", resourcePath);
      return asRelayedEvent(event);
    }

    String resourceContent = payload.getContentAsString();
    ParentResource<?> resource = toParentResource(resourcePath, resourceContent,
        settings.getHandledResourceClass(), payloadType);

    log.infof("Resource %s, having absolute url %s, will be published to StreamX as %s",
        resourcePath, resource.getAbsoluteUrl(), resource.getStreamxKey());

    if (!CloudEventUtils.isPublishingType(eventType)) {
      // TODO implement unpublishing orphaned external resources, for now just relay the event
      // TODO implement it also for publishing an edited resource with some links removed
      return asProcessedEvent(event, resource.getStreamxKey());
    }

    Set<ExternalResource> externalResources = settings.getExternalResourcesCollector()
        .collectExternalResources(resource);

    if (externalResources.isEmpty()) {
      log.tracef("No external resources found for %s", resource.getStreamxKey());
      return asProcessedEvent(event, resource.getStreamxKey());
    }

    log.tracef("Found %d external resources for %s: %s",
        externalResources.size(),
        resource.getStreamxKey(),
        externalResources.stream().map(ExternalResource::getPaths).collect(Collectors.toList())
    );

    return downloadExternalResourcesAndReturnAdjustedResource(
        event, resource, externalResources, settings
    );
  }

  private Uni<CloudEvent> asRelayedEvent(CloudEvent event) {
    return Uni.createFrom().item(event);
  }

  private Uni<CloudEvent> asProcessedEvent(CloudEvent event, String key) {
    CloudEvent adjustedEvent = new CloudEventBuilder(event)
        .withSubject(key)
        .build();
    return Uni.createFrom().item(adjustedEvent);
  }

  private ParentResource<?> toParentResource(String path, String content,
      Class<? extends Resource> handledResourceType, String payloadType) {
    if (WebResource.class.isAssignableFrom(handledResourceType)) {
      // web resources later become available by http as files, so must sanitize their paths
      String sanitizedResourcePath = urlComputationService.asStreamxKey(path);
      String resourceAbsoluteUrl = urlComputationService
          .computeAbsoluteUrlRelativeToConfiguredBaseUrl(sanitizedResourcePath);
      String resourceStreamxKey = urlComputationService
          .asStreamxKeyRelativeToConfiguredBaseUrl(resourceAbsoluteUrl);
      return new ParentResource<>(
          resourceAbsoluteUrl, resourceStreamxKey, content, payloadType, handledResourceType);
    } else {
      return new ParentResource<>(
          configuration.baseUrlForRelativePaths(), path, content, payloadType, handledResourceType);
    }
  }

  private Uni<CloudEvent> downloadExternalResourcesAndReturnAdjustedResource(
      CloudEvent event, ParentResource<?> resource,
      Set<ExternalResource> externalResources, ProcessResourceFunctionSettings<?> settings) {

    return Multi.createFrom().iterable(externalResources)
        .onItem()
        .transformToUniAndMerge(externalResource -> externalResourcesProcessFunction
            .downloadAndPublish(externalResource, resource))
        .collect().last()
        .map(ignored -> {
          String content = resource.getContent();

          log.tracef("Replacing external links in %s", resource.getStreamxKey());
          String adjustedContent = settings.getContentAdjuster()
              .adjustLinks(content, externalResources, log);

          log.tracef("Publishing resource %s with all external links adjusted to local paths",
              resource.getStreamxKey());

          Resource adjustedResource = settings.newResource(adjustedContent, resource.getPayloadType());
          return CloudEventUtils.builderWithJsonData(adjustedResource)
              .withSubject(resource.getStreamxKey())
              .withType(event.getType())
              .build();
        });
  }
}