package com.streamx.blueprints.rewriter.functions;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.rewriter.Channels;
import com.streamx.blueprints.rewriter.data.ExternalResource;
import com.streamx.blueprints.rewriter.data.ResourceData;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class DownloadRequestEmmiterFunction extends BaseProcessResourceFunction {

  @Incoming(Channels.INCOMING_RESOURCES)
  @Outgoing(Channels.DOWNLOAD_REQUESTS)
  public Multi<CloudEvent> emitDownloadRequests(CloudEvent event) {
    return resolveContext(event)
        .map(ctx -> emitDownloadRequestForExternalResources(event, ctx))
        .orElseGet(Multi.createFrom()::empty);
  }

  private Multi<CloudEvent> emitDownloadRequestForExternalResources(CloudEvent event,
      ProcessingContext ctx) {

    if (!CloudEventUtils.isPublishingType(event.getType())) {
      return Multi.createFrom().empty();
    }

    ResourceData resource = collectResourceData(ctx);

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

  private CloudEvent createDownloadRequest(ExternalResource resource) {

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
}
