package com.streamx.blueprints.resourcedownloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.IntegerRange;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpDownloaderFunction {

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
  private static final int NOT_MODIFIED_STATUS = HttpStatus.SC_NOT_MODIFIED;
  private static final IntegerRange SUCCESS_STATUSES = IntegerRange.of(200, 299);

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  LastModifiedTimestampRegistry lastModifiedTimestampRegistry;

  @Channel(Channels.DOWNLOADED_RESOURCES)
  Emitter<CloudEvent> resourcesEmitter;

  @Inject
  CloseableHttpClient httpClient;

  private int downloadTimeoutMillis;

  @PostConstruct
  void init() {
    downloadTimeoutMillis = configuration.downloadTimeoutMilliseconds();
  }

  @Incoming(Channels.DOWNLOAD_REQUESTS)
  public void downloadAndEmit(CloudEvent event) {
    DownloadRequest request = extractDownloadRequest(event);
    if (request == null) {
      return;
    }
    log.tracef("Processing download request: %s", request);

    // TODO: in near future it's planned to register data to Store in a separate @Incoming method
    lastModifiedTimestampRegistry.store(request);

    String url = request.url();
    log.tracef("Processing download request with source URL %s and destination Streamx Key %s",
        url, request.emitKey());

    int httpHeadStatus = lastModifiedTimestampRegistry.getLastHttpHeadStatus(url);
    if (httpHeadStatus == NOT_MODIFIED_STATUS) {
      log.tracef("Skipping downloading unchanged resource %s", url);
      return;
    }

    if (!SUCCESS_STATUSES.contains(httpHeadStatus)) {
      log.warnf("Skipping downloading resource %s with HEAD status %s", url, httpHeadStatus);
      return;
    }

    downloadAndEmit(url, request);
  }

  private void downloadAndEmit(String url, DownloadRequest request) {
    HttpGet httpGetRequest = prepareHttpGetRequest(url);
    try (CloseableHttpResponse httpGetResponse = httpClient.execute(httpGetRequest)) {
      int httpGetStatus = httpGetResponse.getStatusLine().getStatusCode();
      if (SUCCESS_STATUSES.contains(httpGetStatus)) {
        emitResource(request, httpGetResponse);
      } else {
        log.errorf("Error downloading resource %s, unexpected HTTP status: %s", url, httpGetStatus);
      }
    } catch (Exception ex) {
      log.errorf(ex, "Failure downloading resource %s", url);
    }
  }

  private DownloadRequest extractDownloadRequest(CloudEvent event) {
    try {
      DownloadRequest downloadRequest = CloudEventUtils.getData(event, DownloadRequest.class);
      if (downloadRequest == null) {
        log.warnf("Null data in the incoming CloudEvent %s", event.getSubject());
      }
      return downloadRequest;
    } catch (RuntimeException ex) {
      log.warnf("Invalid incoming CloudEvent %s: %s", event.getSubject(), ex.getMessage());
      return null;
    }
  }

  private HttpGet prepareHttpGetRequest(String url) {
    HttpGet request = new HttpGet(url);
    request.setConfig(RequestConfig.copy(RequestConfig.DEFAULT)
        .setConnectTimeout(downloadTimeoutMillis)
        .setSocketTimeout(downloadTimeoutMillis)
        .build());
    return request;
  }

  private void emitResource(DownloadRequest request, CloseableHttpResponse response)
      throws IOException {
    byte[] resourceBytes = getResponseBytes(response);
    ByteBuffer content = ByteBuffer.wrap(resourceBytes);
    String streamxKey = request.emitKey();
    if (isHtmlPage(response)) {
      emit(
          streamxKey,
          new Page(content, request.emittedPageType()),
          Page.TYPE_PUBLISHED
      );
    } else if (isWebResource(response)) {
      emit(
          streamxKey,
          new WebResource(content, request.emittedWebResourceType()),
          WebResource.TYPE_PUBLISHED
      );
    } else {
      emit(
          streamxKey,
          new Asset(content, request.emittedAssetType()),
          Asset.TYPE_PUBLISHED
      );
    }
  }

  private byte[] getResponseBytes(CloseableHttpResponse response) throws IOException {
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

  private <T extends Resource> void emit(String key, T payload, String eventType) {
    String payloadClass = payload.getClass().getSimpleName();
    String payloadType = payload.getType();
    log.tracef("Emitting %s %s with event type %s and payload type %s", payloadClass, key, eventType, payloadType);

    CloudEvent cloudEvent = CloudEventUtils.eventWithData(payload, eventType, key);
    resourcesEmitter.send(cloudEvent);
  }

}
