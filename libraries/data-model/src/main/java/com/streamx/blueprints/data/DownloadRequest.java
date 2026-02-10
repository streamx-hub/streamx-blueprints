package com.streamx.blueprints.data;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record DownloadRequest(
    String url,
    String emitKey,
    String emittedPageType,
    String emittedWebResourceType,
    String emittedAssetType) {

  public static final String DOWNLOAD_REQUEST_EVENT_TYPE =
      "com.streamx.blueprints.download.request.v1";
  public static final String DOWNLOAD_SCHEDULE_EVENT_TYPE =
      "com.streamx.blueprints.download.schedule.v1";
  public static final String DOWNLOAD_UNSCHEDULE_EVENT_TYPE =
      "com.streamx.blueprints.download.unschedule.v1";
}
