package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.data.Asset;
import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.Channels;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.data.ExternalResource;
import dev.streamx.blueprints.externalresources.data.ParentResource;
import dev.streamx.blueprints.externalresources.services.HttpDownloader;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
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

  @Channel(Channels.OUTGOING_PAGES)
  Emitter<Page> pagesEmitter;

  @Channel(Channels.OUTGOING_WEB_RESOURCES)
  Emitter<WebResource> webResourcesEmitter;

  @Channel(Channels.OUTGOING_ASSETS)
  Emitter<Asset> assetsEmitter;

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
          pagesEmitter,
          streamxKey,
          new Page(content),
          configuration.externalPagePublishSxType().orElseThrow()
      );
    } else if (HttpDownloader.isWebResource(response)) {
      emit(
          webResourcesEmitter,
          streamxKey,
          new WebResource(content),
          configuration.externalWebResourcePublishSxType().orElseThrow()
      );
    } else {
      emit(
          assetsEmitter,
          streamxKey,
          new Asset(content),
          null
      );
    }
  }

  private <T> void emit(Emitter<T> emitter, String key, T payload, @Nullable String sxType) {
    String payloadType = payload.getClass().getSimpleName();
    Properties properties = Properties.empty();
    if (sxType == null) {
      tracef("Publishing %s %s without sx:type", payloadType, key);
    } else {
      properties = properties.withType(sxType);
      tracef("Publishing %s %s with sx:type %s", payloadType, key, sxType);
    }

    Metadata metadata = Metadata.of(
        Key.of(key),
        Action.PUBLISH,
        EventTime.of(System.currentTimeMillis()),
        properties
    );
    Message<T> message = Message.of(
        payload,
        metadata
    );
    emitter.send(message);
  }

  void tracef(String format, Object... params) {
    log.tracef(format, params);
  }

  void logDownloadingError(Throwable ex, String absoluteUrl) {
    log.warnf("Error downloading external resource %s - %s: %s", absoluteUrl,
        ex.getClass().getName(), ex.getMessage());
  }
}
