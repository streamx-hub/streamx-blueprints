package com.streamx.blueprints.resource.downloader;

import static com.streamx.blueprints.data.DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE;
import static com.streamx.blueprints.data.DownloadRequest.DOWNLOAD_SCHEDULE_EVENT_TYPE;
import static com.streamx.blueprints.data.DownloadRequest.DOWNLOAD_UNSCHEDULE_EVENT_TYPE;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.Strings;

@ApplicationScoped
public class DownloadRequestClassifier {

  @Inject
  Configuration configuration;

  static boolean shouldDownloadAndEmit(String eventType) {
    return Strings.CS.equalsAny(eventType, DOWNLOAD_REQUEST_EVENT_TYPE,
        DOWNLOAD_SCHEDULE_EVENT_TYPE);
  }

  boolean shouldScheduleRepeatableDownload(String eventType, String url) {
    return eventType.equals(DOWNLOAD_SCHEDULE_EVENT_TYPE) || matchesRepeatableUrlPattern(url);
  }

  static boolean shouldUnscheduleRepeatableDownload(String eventType) {
    return eventType.equals(DOWNLOAD_UNSCHEDULE_EVENT_TYPE);
  }

  private boolean matchesRepeatableUrlPattern(String url) {
    return configuration.repeatableUrlPattern()
        .map(pattern -> pattern.matcher(url).matches())
        .orElse(false);
  }

}
