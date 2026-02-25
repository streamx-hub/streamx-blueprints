package com.streamx.blueprints.resource.downloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.IntegerRange;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpDownloaderFunction {

  private static final IntegerRange SUCCESS_STATUSES = IntegerRange.of(200, 299);

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  CloseableHttpClient httpClient;

  @Inject
  ResourceEmitter resourceEmitter;

  @Inject
  LastModifiedTimestampRegistry lastModifiedTimestampRegistry;

  @Inject
  DownloadRequestClassifier downloadRequestClassifier;

  @Inject
  RepositoryFactory repositoryFactory;

  private int downloadTimeoutMillis;
  private Duration repeatInterval;
  private StateRepository<DownloadRequest> repeatableDownloadsStore;

  void onStart(@Observes StartupEvent ev) {
    downloadTimeoutMillis = configuration.downloadTimeoutMillis();
    repeatInterval = Duration.ofMillis(configuration.repeatIntervalMillis());
    repeatableDownloadsStore = repositoryFactory.getOrCreate(
        "repeatable-downloads", DownloadRequest.class);
    initRepeatableDownloadAndEmit();
  }

  private void initRepeatableDownloadAndEmit() {
    Multi.createFrom().ticks()
        .every(repeatInterval)
        .flatMap(l -> Multi.createFrom().items(repeatableDownloadsStore.values()))
        .subscribe()
        .with(request -> {
          try {
            downloadAndEmit(request);
          } catch (Exception ex) {
            String errorMessage = "Error downloading scheduled resource " + request.url();
            logDownloadError(ex, errorMessage);
          }
        });
  }

  @Incoming(Channels.DOWNLOAD_REQUESTS_STATE)
  public void processState(CloudEvent event) {
    scheduleIfScheduledRequest(event);
  }

  @Incoming(Channels.DOWNLOAD_REQUESTS)
  public CompletionStage<Void> process(Message<CloudEvent> message) {
    CloudEvent event = message.getPayload();
    DownloadRequest request = scheduleIfScheduledRequest(event);
    if (request == null || !DownloadRequestClassifier.shouldDownloadAndEmit(event.getType())) {
      return message.ack();
    }
    try {
      downloadAndEmit(request);
      return message.ack();
    } catch (Exception ex) {
      String errorMessage = "Error downloading resource " + request.url();
      logDownloadError(ex, errorMessage);
      return message.nack(new StackTracelessException(errorMessage, ex));
    }
  }

  private DownloadRequest scheduleIfScheduledRequest(CloudEvent event) {
    DownloadRequest request = CloudEventUtils.getData(event, DownloadRequest.class);
    if (request == null) {
      log.warnf("Received an empty DownloadRequest with key %s", event.getSubject());
      return null;
    }

    String url = request.url();
    String eventType = event.getType();
    log.tracef("Processing %s download request: %s", eventType, request);

    if (downloadRequestClassifier.shouldScheduleRepeatableDownload(eventType, url)) {
      repeatableDownloadsStore.put(url, request);
    }

    if (DownloadRequestClassifier.shouldUnscheduleRepeatableDownload(eventType)) {
      repeatableDownloadsStore.remove(url);
    }

    return request;
  }

  void downloadAndEmit(DownloadRequest request) throws DownloadException {
    String url = request.url();
    log.tracef("Processing download request with source URL %s and destination StreamX Key %s",
        url, request.emitKey());

    HttpGet httpGetRequest = prepareHttpGetRequest(url);
    try (CloseableHttpResponse response = executeGet(httpGetRequest)) {
      lastModifiedTimestampRegistry.storeLastModifiedTimestamp(url, response);
      int status = response.getStatusLine().getStatusCode();
      if (SUCCESS_STATUSES.contains(status)) {
        resourceEmitter.emitResource(response, request);
      } else if (status != HttpStatus.SC_NOT_MODIFIED) {
        lastModifiedTimestampRegistry.remove(url);
        throw new DownloadException("Error downloading resource " + url
                                    + ", unexpected HTTP status: " + status);
      }
    } catch (Exception ex) {
      lastModifiedTimestampRegistry.remove(url);
      throw new DownloadException("Exception at GET request for " + url, ex);
    }
  }

  private HttpGet prepareHttpGetRequest(String url) {
    HttpGet request = new HttpGet(url);
    request.setConfig(RequestConfig.copy(RequestConfig.DEFAULT)
        .setConnectTimeout(downloadTimeoutMillis)
        .setSocketTimeout(downloadTimeoutMillis)
        .build());

    LastModifiedTimestamp lastModifiedTimestamp = lastModifiedTimestampRegistry.get(url);
    if (lastModifiedTimestamp != null) {
      request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, lastModifiedTimestamp.lastModifiedGmt());
    }

    return request;
  }

  CloseableHttpResponse executeGet(HttpGet request) throws IOException {
    return httpClient.execute(request);
  }

  private void logDownloadError(Exception ex, String errorMessage) {
    if (log.isDebugEnabled()) {
      log.debug(errorMessage, ex);
    } else {
      log.error(errorMessage);
    }
  }

}
