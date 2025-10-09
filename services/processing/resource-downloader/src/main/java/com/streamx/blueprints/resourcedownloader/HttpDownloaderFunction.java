package com.streamx.blueprints.resourcedownloader;

import static java.util.Objects.requireNonNull;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

  @Channel(Channels.DOWNLOADED_PAGES)
  Emitter<CloudEvent> pagesEmitter;

  @Channel(Channels.DOWNLOADED_ASSETS)
  Emitter<CloudEvent> assetsEmitter;

  @Channel(Channels.DOWNLOADED_WEB_RESOURCES)
  Emitter<CloudEvent> webResourcesEmitter;

  @Inject
  CloseableHttpClient httpClient;

  private static final Map<String, DownloadRequest> repeatingDownloadsStore =
      new ConcurrentHashMap<>();

  private int downloadTimeoutMillis;

  private long repeatIntervalMillis;

  @Incoming(Channels.DOWNLOAD_REQUESTS)
  public void downloadAndEmit(CloudEvent event) {
    DownloadRequest request = requireNonNull(CloudEventUtils.getData(event, DownloadRequest.class));
    log.tracef("Processing download request: %s", request);

    downloadAndPublish(request);

    String url = request.url();
    if (isRepeatableDownload(url)) {
      repeatingDownloadsStore.put(url, request);
    }
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

  void initOnStart() {
    downloadTimeoutMillis = configuration.downloadTimeoutMilliseconds();
    repeatIntervalMillis = configuration.repeatIntervalMillis();

    initRepeatingDownloadAndEmit();
  }

  private void initRepeatingDownloadAndEmit() {
    Multi.createFrom().ticks()
        .every(Duration.ofMillis(repeatIntervalMillis))
        .flatMap(l -> {
          Collection<DownloadRequest> items = repeatingDownloadsStore.values();
          return Multi.createFrom().iterable(items);
        })
        .onFailure().invoke(err -> {
          log.errorf(err, "Stream processing of scheduled resource has failed: %s",
              err.getMessage());
        })
        .subscribe()
        .with(this::downloadAndPublish);
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
          pagesEmitter,
          streamxKey,
          new Page(content, request.emittedPageType()),
          Page.TYPE_PUBLISHED
      );
    } else if (isWebResource(response)) {
      emit(
          webResourcesEmitter,
          streamxKey,
          new WebResource(content, request.emittedWebResourceType()),
          WebResource.TYPE_PUBLISHED
      );
    } else {
      emit(
          assetsEmitter,
          streamxKey,
          new Asset(content, request.emittedAssetType()),
          Asset.TYPE_PUBLISHED
      );
    }
  }

  private byte[] getResponseBytes(CloseableHttpResponse response) throws IOException {
    return IOUtils.toByteArray(response.getEntity().getContent());
  }

  private void downloadAndPublish(DownloadRequest request) {
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

    downloadAndEmitResource(request);
  }

  private boolean isRepeatableDownload(String url) {
    return configuration.urlRepeatingPattern()
        .map(pattern -> pattern.matcher(url).matches())
        .orElse(false);
  }

  private void downloadAndEmitResource(DownloadRequest request) {
    String url = request.url();
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

  private <T extends Resource> void emit(Emitter<CloudEvent> emitter, String key, T payload,
      String eventType) {
    String payloadClass = payload.getClass().getSimpleName();
    String payloadType = payload.getType();
    log.tracef("Emitting %s %s with event type %s and payload type %s", payloadClass, key, eventType, payloadType);

    CloudEvent cloudEvent = CloudEventUtils.eventWithData(payload, eventType, key);
    emitter.send(cloudEvent);
  }

  void onStart(@Observes StartupEvent ev) {
    initOnStart();
  }


}
