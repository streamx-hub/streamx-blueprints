package com.streamx.blueprints.data;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record DownloadRequest(
    String url,
    String emitKey,
    String emittedPageType,
    String emittedWebResourceType,
    String emittedAssetType) {

  public static final String DOWNLOAD_EVENT_TYPE =
      "com.streamx.blueprints.download-request.v1";
  public static final String REPEATABLE_DOWNLOAD_EVENT_TYPE =
      "com.streamx.blueprints.repeatable-download-request.v1";
  public static final String STOP_REPEATABLE_DOWNLOAD_EVENT_TYPE =
      "com.streamx.blueprints.stop-repeatable-download-request.v1";
}
