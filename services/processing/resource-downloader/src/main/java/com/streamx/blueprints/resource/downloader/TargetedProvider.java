package com.streamx.blueprints.resource.downloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.Targeted;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.http.HttpHeaders;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TargetedProvider {

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

  private static final String CONTENT_TYPE_HEADER = HttpHeaders.CONTENT_TYPE.toString();

  @Inject
  Logger log;

  Targeted getTargeted(HttpResponse<Buffer> response, DownloadRequest request) {
    byte[] resourceBytes = getResponseBytes(response);
    ByteBuffer content = ByteBuffer.wrap(resourceBytes);
    String streamxKey = request.emitKey();
    if (isHtmlPage(response)) {
      CloudEvent event = createCloudEvent(
          streamxKey,
          new Page(content, request.emittedPageType()),
          Page.TYPE_PUBLISHED
      );
      return Targeted.of(Channels.DOWNLOADED_PAGES, event);

    } else if (isWebResource(response)) {
      CloudEvent event = createCloudEvent(
          streamxKey,
          new WebResource(content, request.emittedWebResourceType()),
          WebResource.TYPE_PUBLISHED
      );
      return Targeted.of(Channels.DOWNLOADED_WEB_RESOURCES, event);
    } else {
      CloudEvent event = createCloudEvent(
          streamxKey,
          new Asset(content, request.emittedAssetType()),
          Asset.TYPE_PUBLISHED
      );
      return Targeted.of(Channels.DOWNLOADED_ASSETS, event);
    }
  }

  <T extends Resource> CloudEvent createCloudEvent(String key, T payload, String eventType) {
    String payloadClass = payload.getClass().getSimpleName();
    String payloadType = payload.getType();
    log.tracef("Emitting %s at key %s with event type %s and payload type %s", payloadClass, key,
        eventType, payloadType);

    return CloudEventUtils.eventWithData(key, eventType, payload);
  }

  private static byte[] getResponseBytes(HttpResponse<Buffer> response) {
    return response.bodyAsBuffer().getBytes();
  }

  private static boolean isHtmlPage(HttpResponse<Buffer> response) {
    return contentTypeStartsWithAny(response, PAGE_CONTENT_TYPES);
  }

  private static boolean isWebResource(HttpResponse<Buffer> response) {
    return contentTypeStartsWithAny(response, WEB_RESOURCE_CONTENT_TYPES);
  }

  private static boolean contentTypeStartsWithAny(HttpResponse<Buffer> response,
      String... prefixes) {
    String contentTypeHeader = response.getHeader(CONTENT_TYPE_HEADER);
    if (contentTypeHeader == null) {
      return false;
    }
    return StringUtils.startsWithAny(contentTypeHeader, prefixes);
  }

}
