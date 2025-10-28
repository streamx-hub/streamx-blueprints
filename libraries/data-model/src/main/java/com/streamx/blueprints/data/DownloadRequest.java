package com.streamx.blueprints.data;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record DownloadRequest(
    String url,
    String emitKey,
    String emittedPageType,
    String emittedWebResourceType,
    String emittedAssetType) {

  public static final String EVENT_TYPE = "com.streamx.blueprints.resource-download.request.v1";

}
