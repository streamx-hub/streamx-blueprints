package com.streamx.blueprints.resource.downloader;

import static com.streamx.blueprints.data.DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE;
import static com.streamx.blueprints.data.DownloadRequest.DOWNLOAD_SCHEDULE_EVENT_TYPE;
import static com.streamx.blueprints.data.DownloadRequest.DOWNLOAD_UNSCHEDULE_EVENT_TYPE;

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
import java.time.Duration;
import org.apache.commons.lang3.IntegerRange;
import org.apache.commons.lang3.Strings;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpDownloaderFunction extends BaseHttpRequestExecutor {

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
  RepositoryFactory repositoryFactory;

  private StateRepository<DownloadRequest> repeatableDownloadsStore;

  private int downloadTimeoutMillis;
  private Duration repeatInterval;

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
        .flatMap(l -> Multi.createFrom().iterable(repeatableDownloadsStore.values()))
        .subscribe()
        .with(request -> {
          try {
            downloadAndEmit(request);
          } catch (Exception ex) {
            log.warnf("Error downloading repeatable resource " + request.url());
          }
        });
  }

  @Incoming(Channels.DOWNLOAD_REQUESTS_STATE)
  public void processState(CloudEvent event) {
    scheduleIfScheduledRequest(event);
  }

  @Incoming(Channels.DOWNLOAD_REQUESTS)
  public void process(CloudEvent event) throws Exception {
    DownloadRequest request = scheduleIfScheduledRequest(event);
    if (request != null && shouldDownloadAndEmit(event.getType())) {
      downloadAndEmit(request);
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

    if (shouldScheduleRepeatableDownload(eventType, url)) {
      repeatableDownloadsStore.put(url, request);
    }

    if (shouldUnscheduleRepeatableDownload(eventType)) {
      repeatableDownloadsStore.remove(url);
    }

    return request;
  }

  private static boolean shouldDownloadAndEmit(String eventType) {
    return Strings.CS.equalsAny(eventType, DOWNLOAD_REQUEST_EVENT_TYPE,
        DOWNLOAD_SCHEDULE_EVENT_TYPE);
  }

  private boolean shouldScheduleRepeatableDownload(String eventType, String url) {
    return eventType.equals(DOWNLOAD_SCHEDULE_EVENT_TYPE) || matchesRepeatableUrlPattern(url);
  }

  private static boolean shouldUnscheduleRepeatableDownload(String eventType) {
    return eventType.equals(DOWNLOAD_UNSCHEDULE_EVENT_TYPE);
  }

  private boolean matchesRepeatableUrlPattern(String url) {
    return configuration.repeatableUrlPattern()
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

  void downloadAndEmit(DownloadRequest request) throws DownloadException {
    String url = request.url();
    log.tracef("Processing download request with source URL %s and destination StreamX Key %s",
        url, request.emitKey());

    lastModifiedTimestampRegistry.store(url);

    int httpHeadStatus = lastModifiedTimestampRegistry.getLastHttpHeadStatus(url);
    if (httpHeadStatus == HttpStatus.SC_NOT_MODIFIED) {
      log.tracef("Skipping downloading unchanged resource %s", url);
      return;
    }

    if (!SUCCESS_STATUSES.contains(httpHeadStatus)) {
      throw new DownloadException("Error downloading resource " + url
                                  + ", unexpected HTTP HEAD status: " + httpHeadStatus);
    }

    performDownloadAndEmit(request);
  }

  private void performDownloadAndEmit(DownloadRequest request) throws DownloadException {
    String url = request.url();
    HttpGet httpGetRequest = prepareHttpGetRequest(url);
    try (CloseableHttpResponse httpGetResponse = executeGet(httpGetRequest)) {
      int httpGetStatus = httpGetResponse.getStatusLine().getStatusCode();
      if (SUCCESS_STATUSES.contains(httpGetStatus)) {
        resourceEmitter.emitResource(httpGetResponse, request);
      } else {
        lastModifiedTimestampRegistry.reset(url);
        throw new DownloadException("Error downloading resource " + url
                                    + ", unexpected HTTP GET status: " + httpGetStatus);
      }
    } catch (Exception ex) {
      lastModifiedTimestampRegistry.reset(url);
      throw new DownloadException("Exception at GET request for " + url, ex);
    }
  }

}
