package com.streamx.blueprints.resource.downloader;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.Targeted;
import io.smallrye.reactive.messaging.annotations.Outgoings;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.http.HttpHeaders;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Map;
import org.apache.commons.lang3.IntegerRange;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpDownloaderFunction {

  private static final IntegerRange SUCCESS_STATUSES = IntegerRange.of(200, 299);

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  TargetedProvider targetedProvider;

  @Inject
  LastModifiedTimestampRegistry lastModifiedTimestampRegistry;

  @Inject
  DownloadRequestClassifier downloadRequestClassifier;

  @Inject
  RepositoryFactory repositoryFactory;

  @Inject
  WebClient webClient;
  private int downloadTimeoutMillis;
  private Duration repeatInterval;
  private StateRepository<DownloadRequest> repeatableDownloadsStore;

  @PostConstruct
  void setup() {
    downloadTimeoutMillis = configuration.downloadTimeoutMillis();
    repeatInterval = Duration.ofMillis(configuration.repeatIntervalMillis());
    repeatableDownloadsStore = repositoryFactory.getOrCreate(
        "repeatable-downloads", DownloadRequest.class);
  }

  @Incoming(Channels.DOWNLOAD_REQUESTS_STATE)
  public void processState(CloudEvent event) {
    scheduleIfScheduledRequest(event);
  }

  @Outgoings({
      @Outgoing(Channels.DOWNLOADED_PAGES),
      @Outgoing(Channels.DOWNLOADED_ASSETS),
      @Outgoing(Channels.DOWNLOADED_WEB_RESOURCES)})
  public Multi<Targeted> processScheduled() {
    return Multi.createFrom().ticks()
        .every(repeatInterval)
        .flatMap(l -> Multi.createFrom().items(repeatableDownloadsStore.values()))
        .onItem().transformToUniAndMerge(item ->
            downloadAndChooseTarget(item)
                .onFailure().recoverWithUni(Uni.createFrom().nothing())
        );
  }

  @Incoming(Channels.DOWNLOAD_REQUESTS)
  @Outgoings({
      @Outgoing(Channels.DOWNLOADED_PAGES),
      @Outgoing(Channels.DOWNLOADED_ASSETS),
      @Outgoing(Channels.DOWNLOADED_WEB_RESOURCES)})
  public Uni<Targeted> process(Message<CloudEvent> message) {
    CloudEvent event = message.getPayload();
    DownloadRequest request = scheduleIfScheduledRequest(event);

    if (request == null || !DownloadRequestClassifier.shouldDownloadAndEmit(event.getType())) {
      message.ack();
      return Uni.createFrom().item(Targeted.from(Map.of()));
    }

    return downloadAndChooseTarget(request).onItem().invoke(() -> message.ack())
        .onFailure().recoverWithUni(err -> {
          message.nack(err);
          return Uni.createFrom().item(Targeted.from(Map.of()));
        });
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

  Uni<Targeted> downloadAndChooseTarget(DownloadRequest request) {
    String url = request.url();
    log.tracef("Processing download request with source URL %s and destination StreamX Key %s",
        url, request.emitKey());

    return get(url).onItem().transform(response -> {
      lastModifiedTimestampRegistry.storeLastModifiedTimestamp(url, response);
      int status = response.statusCode();
      if (SUCCESS_STATUSES.contains(status)) {
        return targetedProvider.getTargeted(response, request);
      } else if (status != 304) {
        lastModifiedTimestampRegistry.remove(url);
        DownloadException exception = new DownloadException("Error downloading resource " + url
            + ", unexpected HTTP status: " + status);
        logDownloadError(exception, exception.getMessage());
        throw new RuntimeException(exception);
      }
      return Targeted.from(Map.of());
    });
  }


  public Uni<HttpResponse<Buffer>> get(String url) {

    HttpRequest<Buffer> request = webClient
        .getAbs(url)
        .timeout(downloadTimeoutMillis);
    request.putHeader("Accept-Encoding", "gzip, deflate");

    LastModifiedTimestamp lastModifiedTimestamp = lastModifiedTimestampRegistry.get(url);

    if (lastModifiedTimestamp != null) {
      request.putHeader(HttpHeaders.IF_MODIFIED_SINCE.toString(),
          lastModifiedTimestamp.lastModifiedGmt()
      );
    }
    return request.send();
  }

  private void logDownloadError(Throwable throwable, String errorMessage) {
    if (log.isDebugEnabled()) {
      log.debug(errorMessage, throwable);
    } else {
      log.warn(errorMessage);
    }
  }

}
