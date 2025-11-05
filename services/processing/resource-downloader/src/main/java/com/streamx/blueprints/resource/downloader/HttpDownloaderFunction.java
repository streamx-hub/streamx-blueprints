package com.streamx.blueprints.resource.downloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.IntegerRange;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpDownloaderFunction {

  private static final int NOT_MODIFIED_STATUS = HttpStatus.SC_NOT_MODIFIED;
  private static final IntegerRange SUCCESS_STATUSES = IntegerRange.of(200, 299);

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  ResourceEmitter resourceEmitter;

  @Inject
  LastModifiedTimestampRegistry lastModifiedTimestampRegistry;

  @Inject
  CloseableHttpClient httpClient;

  private static final Map<String, DownloadRequest> repeatingDownloadsStore =
      new ConcurrentHashMap<>();

  private int downloadTimeoutMillis;

  private long repeatIntervalMillis;

  void onStart(@Observes StartupEvent ev) {
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
        .onFailure().invoke(err ->
            log.errorf(err, "Stream processing of scheduled resource has failed: %s",
                err.getMessage()))
        .subscribe()
        .with(this::downloadAndEmit);
  }

  @Incoming(Channels.DOWNLOAD_REQUESTS)
  public void process(CloudEvent event) {
    DownloadRequest request = CloudEventUtils.getData(event, DownloadRequest.class);
    if (request == null) {
      log.warnf("Received an empty DownloadRequest with key %s", event.getSubject());
      return;
    }

    log.tracef("Processing download request: %s", request);

    downloadAndEmit(request);

    String url = request.url();
    if (isRepeatableDownload(url)) {
      repeatingDownloadsStore.put(url, request);
    }
  }

  private boolean isRepeatableDownload(String url) {
    return configuration.urlRepeatingPattern()
        .map(pattern -> pattern.matcher(url).matches())
        .orElse(false);
  }

  private HttpGet prepareHttpGetRequest(String url) {
    HttpGet request = new HttpGet(url);
    request.setConfig(RequestConfig.copy(RequestConfig.DEFAULT)
        .setConnectTimeout(downloadTimeoutMillis)
        .setSocketTimeout(downloadTimeoutMillis)
        .build());
    return request;
  }

  private void downloadAndEmit(DownloadRequest request) {
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

    HttpGet httpGetRequest = prepareHttpGetRequest(url);
    try (CloseableHttpResponse httpGetResponse = httpClient.execute(httpGetRequest)) {
      int httpGetStatus = httpGetResponse.getStatusLine().getStatusCode();
      if (SUCCESS_STATUSES.contains(httpGetStatus)) {
        resourceEmitter.emitResource(httpGetResponse, request);
      } else {
        log.errorf("Error downloading resource %s, unexpected HTTP status: %s", url, httpGetStatus);
      }
    } catch (Exception ex) {
      log.errorf(ex, "Failure downloading resource %s", url);
    }
  }

  void resetStore() {
    repeatingDownloadsStore.clear();
  }

}
