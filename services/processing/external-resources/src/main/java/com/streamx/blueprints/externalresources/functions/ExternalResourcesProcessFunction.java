package com.streamx.blueprints.externalresources.functions;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.externalresources.Channels;
import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.data.ExternalResource;
import com.streamx.blueprints.externalresources.data.ParentResource;
import com.streamx.blueprints.externalresources.services.HttpDownloader;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * Downloads external resource and emits it to the outgoing channel
 */
@ApplicationScoped
public class ExternalResourcesProcessFunction {

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  HttpDownloader httpDownloader;

  @Channel(Channels.OUTGOING_RESOURCES)
  Emitter<CloudEvent> resourcesEmitter;

  public Uni<Boolean> downloadAndPublish(ExternalResource resource,
      ParentResource<? extends Resource> parentResource) {
    String absoluteUrl = resource.getAbsoluteUrl();
    String streamxKey = resource.getStreamxKey();

    return Uni.createFrom().deferred(() ->
        httpDownloader
            .download(absoluteUrl)
            .flatMap(response -> {
              if (HttpDownloader.isHtmlPage(response) && parentResource.getType() == Page.class) {
                log.errorf("Handling page referenced by another page is unsupported."
                           + " Please adjust configuration of the service. "
                           + " The found referenced page is: %s", absoluteUrl);
                return Uni.createFrom().item(false);
              }
              if (HttpDownloader.isUnchanged(response)) {
                tracef("Not downloading unchanged external resource %s", absoluteUrl);
              } else {
                tracef("Success downloading %s (referenced by parent resource %s) as %s",
                    absoluteUrl, parentResource.getStreamxKey(), streamxKey);
                publishExternalResource(absoluteUrl, streamxKey, response);
              }
              return Uni.createFrom().item(true);
            })
            .onFailure()
            .recoverWithUni(ex -> {
              logDownloadingError(ex, absoluteUrl);
              return Uni.createFrom().item(false);
            })
    );
  }

  private void publishExternalResource(String absoluteUrl, String streamxKey,
      HttpResponse<Buffer> response) {
    byte[] resourceBytes = httpDownloader.getResponseBytes(response, absoluteUrl);
    ByteBuffer content = ByteBuffer.wrap(resourceBytes);
    if (HttpDownloader.isHtmlPage(response)) {
      emit(
          streamxKey,
          new Page(content, configuration.externalPagePublishPayloadType()),
          Page.TYPE_PUBLISHED
      );
    } else if (HttpDownloader.isWebResource(response)) {
      emit(
          streamxKey,
          new WebResource(content, configuration.externalWebResourcePublishPayloadType()),
          WebResource.TYPE_PUBLISHED
      );
    } else {
      emit(
          streamxKey,
          new Asset(content, configuration.externalAssetPublishPayloadType()),
          Asset.TYPE_PUBLISHED
      );
    }
  }

  private <T extends Resource> void emit(String key, T payload, String eventType) {
    String payloadClass = payload.getClass().getSimpleName();
    String payloadType = payload.getType();
    tracef("Publishing %s %s with event type %s and payload type %s", payloadClass, key, eventType, payloadType);

    CloudEvent cloudEvent = CloudEventUtils.builderWithJsonData(payload)
        .withSubject(key)
        .withType(eventType)
        .build();

    resourcesEmitter.send(cloudEvent);
  }

  void tracef(String format, Object... params) {
    log.tracef(format, params);
  }

  void logDownloadingError(Throwable ex, String absoluteUrl) {
    log.warnf("Error downloading external resource %s - %s: %s", absoluteUrl,
        ex.getClass().getName(), ex.getMessage());
  }
}
