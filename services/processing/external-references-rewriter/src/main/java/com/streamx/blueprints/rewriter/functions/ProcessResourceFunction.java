package com.streamx.blueprints.rewriter.functions;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.rewriter.Channels;
import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.data.ExternalResource;
import com.streamx.blueprints.rewriter.data.ResourceData;
import com.streamx.blueprints.rewriter.functions.settings.BaseProcessingSettings;
import com.streamx.blueprints.rewriter.services.UrlComputationService;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;
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
  Instance<BaseProcessingSettings<?>> processingSettings;

  @Incoming(Channels.INCOMING_RESOURCES)
  @Outgoing(Channels.OUTGOING_RESOURCES)
  public Uni<CloudEvent> processIncomingEvent(CloudEvent event) {

    return resolveContext(event)
        .map(ctx -> processIncomingResource(
            event,
            ctx
        ))
        .orElseGet(() -> Uni.createFrom().item(event));
  }

  @Incoming(Channels.INCOMING_RESOURCES)
  @Outgoing(Channels.DOWNLOAD_REQUESTS)
  public Multi<CloudEvent> emitDownloadRequests(CloudEvent event) {
    return resolveContext(event)
        .map(ctx -> processIncomingResourceForExternalDownloads(event, ctx))
        .orElseGet(Multi.createFrom()::empty);
  }

  private Optional<ProcessingContext> resolveContext(CloudEvent event) {
    Resource payload = extractResource(event);
    if (payload == null) {
      return Optional.empty();
    }

    String resourcePath = CloudEventUtils.getSubject(event);
    String payloadType = payload.getType();

    if (!configuration.processablePayloadTypes().contains(payloadType)) {
      log.tracef("Skipping processing %s - unsupported payload type %s", resourcePath,
          payloadType
      );
      return Optional.empty();
    }

    Optional<BaseProcessingSettings<?>> settingsOpt = processingSettings.stream()
        .filter(setting -> setting.handledCloudEventType(event.getType()))
        .filter(setting -> setting.handlesResourcePath(resourcePath))
        .findFirst();

    if (settingsOpt.isEmpty()) {
      log.tracef("No handler for resource event %s of type %s", resourcePath,
          event.getType()
      );
      return Optional.empty();
    }

    BaseProcessingSettings<?> settings = settingsOpt.get();

    if (!settings.getExternalResourcesCollector().hasResourceSelectors()) {
      log.tracef("Skipping processing %s - no resource selectors specified", resourcePath);
      return Optional.empty();
    }

    return Optional.of(new ProcessingContext(payload, payloadType, resourcePath, settings));
  }

  private Multi<CloudEvent> processIncomingResourceForExternalDownloads(CloudEvent event,
      ProcessingContext ctx) {

    if (!isPublishingEvent(event)) {
      return Multi.createFrom().empty();
    }

    ResourceData resource = toResourceData(ctx);

    Set<ExternalResource> externalResources =
        ctx.settings()
            .getExternalResourcesCollector()
            .collectExternalResources(resource);

    if (externalResources.isEmpty()) {
      return Multi.createFrom().empty();
    }

    return Multi.createFrom()
        .items(externalResources.stream()
            .map(this::createDownloadRequest));
  }

  private Uni<CloudEvent> processIncomingResource(CloudEvent event, ProcessingContext ctx) {
    ResourceData resource = toResourceData(ctx);

    log.infof("Resource %s, having absolute url %s, will be published to StreamX as %s",
        ctx.resourcePath(),
        resource.absoluteUrl(),
        resource.streamxKey()
    );

    if (!isPublishingEvent(event)) {
      // TODO implement unpublishing orphaned external resources
      // TODO implement handling removed links after edit
      return asProcessedEvent(event, resource.streamxKey());
    }

    Set<ExternalResource> externalResources =
        ctx.settings()
            .getExternalResourcesCollector()
            .collectExternalResources(resource);

    if (externalResources.isEmpty()) {
      log.tracef("No external resources found for %s", resource.streamxKey());
      return asProcessedEvent(event, resource.streamxKey());
    }

    log.tracef(
        "Found %d external resources for %s: %s",
        externalResources.size(),
        resource.streamxKey(),
        externalResources.stream()
            .map(ExternalResource::getPaths)
            .collect(Collectors.toList())
    );

    return adjustResource(event, resource, externalResources, ctx.settings());
  }

  private ResourceData toResourceData(ProcessingContext ctx) {

    return collectResourceData(
        ctx.resourcePath(),
        ctx.payload().getContentAsString(),
        ctx.settings().getHandledResourceClass(),
        ctx.payloadType()
    );
  }

  private boolean isPublishingEvent(CloudEvent event) {
    return CloudEventUtils.isPublishingType(event.getType());
  }

  public CloudEvent createDownloadRequest(ExternalResource resource) {

    String absoluteUrl = resource.getAbsoluteUrl();
    String streamxKey = resource.getStreamxKey();

    DownloadRequest downloadRequest = new DownloadRequest(
        absoluteUrl,
        streamxKey,
        configuration.emittedPageType(),
        configuration.emittedWebResourceType(),
        configuration.emittedAssetType()
    );

    log.tracef("Sending download request for %s", absoluteUrl);

    return CloudEventUtils.eventWithData(
        streamxKey,
        DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE,
        downloadRequest
    );
  }

  private record ProcessingContext(Resource payload, String payloadType,
                                   String resourcePath, BaseProcessingSettings<?> settings) {
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

  private Uni<CloudEvent> asProcessedEvent(CloudEvent event, String key) {
    return Uni.createFrom().item(new CloudEventBuilder(event)
        .withSubject(key)
        .build());
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

  private Uni<CloudEvent> adjustResource(
      CloudEvent event, ResourceData resource,
      Set<ExternalResource> externalResources, BaseProcessingSettings<?> settings) {

    String resourceStreamxKey = resource.streamxKey();
    log.tracef("Sending download and publish requests for %d external resources of %s",
        externalResources.size(), resourceStreamxKey);

    String content = resource.content();
    log.tracef("Replacing external links in content of %s", resourceStreamxKey);
    String adjustedContent = settings.getContentAdjuster()
        .adjustLinks(content, externalResources);
    Resource adjustedResource = settings.newResource(adjustedContent, resource.payloadType());

    log.tracef("Publishing resource %s with all external links adjusted to local paths",
        resourceStreamxKey);

    return Uni.createFrom().item(CloudEventUtils.eventCopyWithData(event, adjustedResource)
        .withSubject(resource.streamxKey())
        .build());
  }
}