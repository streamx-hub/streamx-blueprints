package com.streamx.blueprints.resource.downloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ResourceEmitter {

  private static final String[] PAGE_CONTENT_TYPES = {
      "application/xhtml+xml",
      "text/html"
  };

  private static final String[] WEB_RESOURCE_CONTENT_TYPES = {
      "application/json",
      "application/xml",
      "application/javascript",
      "text/plain",
      "text/xml",
      "text/javascript",
      "text/css"
  };

  private static final String CONTENT_TYPE_HEADER = HttpHeaders.CONTENT_TYPE;

  @Inject
  Logger log;

  @Channel(Channels.DOWNLOADED_PAGES)
  Emitter<CloudEvent> pagesEmitter;

  @Channel(Channels.DOWNLOADED_ASSETS)
  Emitter<CloudEvent> assetsEmitter;

  @Channel(Channels.DOWNLOADED_WEB_RESOURCES)
  Emitter<CloudEvent> webResourcesEmitter;

  Uni<Void> emitResource(CloseableHttpResponse response, DownloadRequest request)
      throws IOException {
    byte[] resourceBytes = getResponseBytes(response);
    ByteBuffer content = ByteBuffer.wrap(resourceBytes);
    String streamxKey = request.emitKey();
    if (isHtmlPage(response)) {
      return emit(
          pagesEmitter,
          streamxKey,
          new Page(content, request.emittedPageType()),
          Page.TYPE_PUBLISHED
      );
    } else if (isWebResource(response)) {
      return emit(
          webResourcesEmitter,
          streamxKey,
          new WebResource(content, request.emittedWebResourceType()),
          WebResource.TYPE_PUBLISHED
      );
    } else {
      return emit(
          assetsEmitter,
          streamxKey,
          new Asset(content, request.emittedAssetType()),
          Asset.TYPE_PUBLISHED
      );
    }
  }

  <T extends Resource> Uni<Void> emit(Emitter<CloudEvent> emitter, String key, T payload,
      String eventType) {
    String payloadClass = payload.getClass().getSimpleName();
    String payloadType = payload.getType();
    log.tracef("Emitting %s at key %s with event type %s and payload type %s", payloadClass, key,
        eventType, payloadType);

    CloudEvent cloudEvent = CloudEventUtils.eventWithData(key, eventType, payload);
    return Uni.createFrom().completionStage(emitter.send(cloudEvent));
  }

  private static byte[] getResponseBytes(CloseableHttpResponse response) throws IOException {
    return IOUtils.toByteArray(response.getEntity().getContent());
  }

  private static boolean isHtmlPage(CloseableHttpResponse response) {
    return contentTypeStartsWithAny(response, PAGE_CONTENT_TYPES);
  }

  private static boolean isWebResource(CloseableHttpResponse response) {
    return contentTypeStartsWithAny(response, WEB_RESOURCE_CONTENT_TYPES);
  }

  private static boolean contentTypeStartsWithAny(CloseableHttpResponse response,
      String... prefixes) {
    Header contentTypeHeader = response.getFirstHeader(CONTENT_TYPE_HEADER);
    if (contentTypeHeader == null) {
      return false;
    }
    return StringUtils.startsWithAny(contentTypeHeader.getValue(), prefixes);
  }

}
