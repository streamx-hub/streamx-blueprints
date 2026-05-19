package com.streamx.blueprints.rewriter.functions;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.rewriter.Channels;
import com.streamx.blueprints.rewriter.data.ExternalResource;
import com.streamx.blueprints.rewriter.data.ResourceData;
import com.streamx.blueprints.rewriter.functions.settings.BaseProcessingSettings;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class AdjustExternalResourcesFunction extends BaseProcessResourceFunction {

  @Incoming(Channels.INCOMING_RESOURCES)
  @Outgoing(Channels.OUTGOING_RESOURCES)
  public Uni<CloudEvent> adjustExternalResources(CloudEvent event) {

    return resolveContext(event)
        .map(ctx -> adjustExternalResources(
            event,
            ctx
        ))
        .orElseGet(() -> Uni.createFrom().item(event));
  }

  private Uni<CloudEvent> adjustExternalResources(CloudEvent event, ProcessingContext ctx) {
    ResourceData resource = collectResourceData(ctx);

    log.infof("Resource %s, having absolute url %s, will be published to StreamX as %s",
        ctx.resourcePath(),
        resource.absoluteUrl(),
        resource.streamxKey()
    );

    if (!CloudEventUtils.isPublishingType(event.getType())) {
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

  private Uni<CloudEvent> asProcessedEvent(CloudEvent event, String key) {
    return Uni.createFrom().item(new CloudEventBuilder(event)
        .withSubject(key)
        .build());
  }

  private Uni<CloudEvent> adjustResource(
      CloudEvent event, ResourceData resource,
      Set<ExternalResource> externalResources, BaseProcessingSettings<?> settings) {

    String resourceStreamxKey = resource.streamxKey();

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
