package com.streamx.blueprints.rewriter.functions;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.rewriter.Channels;
import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.data.ExternalResource;
import com.streamx.blueprints.rewriter.data.ResourceData;
import com.streamx.blueprints.rewriter.functions.settings.BaseProcessingSettings;
import com.streamx.blueprints.rewriter.services.DownloadRequestsSender;
import com.streamx.blueprints.rewriter.services.UrlComputationService;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessResourceFunction {

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  UrlComputationService urlComputationService;

  @Inject
  DownloadRequestsSender downloadRequestsSender;

  @Inject
  Instance<BaseProcessingSettings<?>> processingSettings;

  @Incoming(Channels.INCOMING_RESOURCES)
  @Outgoing(Channels.OUTGOING_RESOURCES)
  public Uni<CloudEvent> processIncomingEvent(CloudEvent event) {
    Resource payload = extractResource(event);
    if (payload == null) {
      return asRelayedEvent(event);
    }

    String resourcePath = CloudEventUtils.getSubject(event);
    String payloadType = payload.getType();
    if (!configuration.processablePayloadTypes().contains(payloadType)) {
      log.tracef("Skipping processing %s - the service is not configured to handle payload type %s",
          resourcePath, payloadType);
      return asRelayedEvent(event);
    }

    String eventType = event.getType();
    var settingsOpt = processingSettings.stream()
        .filter(setting -> setting.handledCloudEventType(eventType))
        .filter(setting -> setting.handlesResourcePath(resourcePath))
        .findFirst();
    if (settingsOpt.isEmpty()) {
      log.tracef("No handler for resource event %s of type %s", resourcePath, eventType);
      return asRelayedEvent(event);
    }

    BaseProcessingSettings<?> settings = settingsOpt.get();
    if (!settings.getExternalResourcesCollector().hasResourceSelectors()) {
      log.tracef("Skipping processing %s - no resource selectors are specified", resourcePath);
      return asRelayedEvent(event);
    }

    return processIncomingResource(payload, payloadType, resourcePath, event, settings);
  }

  private Uni<CloudEvent> processIncomingResource(Resource payload, String payloadType,
      String resourcePath, CloudEvent event, BaseProcessingSettings<?> settings) {
    String resourceContent = payload.getContentAsString();
    ResourceData resource = collectResourceData(resourcePath, resourceContent,
        settings.getHandledResourceClass(), payloadType);

    log.infof("Resource %s, having absolute url %s, will be published to StreamX as %s",
        resourcePath, resource.absoluteUrl(), resource.streamxKey());

    if (!CloudEventUtils.isPublishingType(event.getType())) {
      // TODO implement unpublishing orphaned external resources, for now just relay the event
      // TODO implement it also for publishing an edited resource with some links removed
      return asProcessedEvent(event, resource.streamxKey());
    }

    Set<ExternalResource> externalResources = settings.getExternalResourcesCollector()
        .collectExternalResources(resource);

    if (externalResources.isEmpty()) {
      log.tracef("No external resources found for %s", resource.streamxKey());
      return asProcessedEvent(event, resource.streamxKey());
    }

    log.tracef("Found %d external resources for %s: %s",
        externalResources.size(),
        resource.streamxKey(),
        externalResources.stream().map(ExternalResource::getPaths).collect(Collectors.toList())
    );
    CloudEvent cloudEvent = downloadExternalResourcesAndReturnAdjustedResource(
        event, resource, externalResources, settings
    );
    return Uni.createFrom().item(cloudEvent);
  }

  private Resource extractResource(CloudEvent event) {
    try {
      Resource resource = CloudEventUtils.getData(event, Resource.class);
      if (resource == null) {
        log.tracef("Skipping processing %s - payload is null - cannot determine payload type",
            event.getSubject());
      }
      return resource;
    } catch (RuntimeException ex) {
      log.warnf("Invalid incoming CloudEvent %s: %s", event.getSubject(), ex.getMessage());
      return null;
    }
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

  private ResourceData collectResourceData(String path, String content,
      Class<? extends Resource> handledResourceType, String payloadType) {
    if (WebResource.class.isAssignableFrom(handledResourceType)) {
      // web resources later become available by http as files, so must sanitize their paths
      String sanitizedResourcePath = urlComputationService.asStreamxKey(path);
      String resourceAbsoluteUrl = urlComputationService
          .computeAbsoluteUrlRelativeToConfiguredBaseUrl(sanitizedResourcePath);
      String resourceStreamxKey = urlComputationService
          .asStreamxKeyRelativeToConfiguredBaseUrl(resourceAbsoluteUrl);
      return new ResourceData(
          resourceAbsoluteUrl, resourceStreamxKey, content, payloadType);
    } else {
      return new ResourceData(
          configuration.baseUrlForRelativePaths(), path, content, payloadType);
    }
  }

  private CloudEvent downloadExternalResourcesAndReturnAdjustedResource(
      CloudEvent event, ResourceData resource,
      Set<ExternalResource> externalResources, BaseProcessingSettings<?> settings) {

    String resourceStreamxKey = resource.streamxKey();
    log.tracef("Sending download and publish requests for %d external resources of %s",
        externalResources.size(), resourceStreamxKey);
    externalResources.forEach(downloadRequestsSender::sendRequest);

    String content = resource.content();
    log.tracef("Replacing external links in content of %s", resourceStreamxKey);
    String adjustedContent = settings.getContentAdjuster().adjustLinks(content, externalResources);
    Resource adjustedResource = settings.newResource(adjustedContent, resource.payloadType());

    log.tracef("Publishing resource %s with all external links adjusted to local paths",
        resourceStreamxKey);

    return CloudEventUtils.eventWithData(resource.streamxKey(), event.getType(), adjustedResource);
  }
}