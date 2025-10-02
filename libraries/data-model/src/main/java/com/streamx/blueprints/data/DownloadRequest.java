package com.streamx.blueprints.data;

public record DownloadRequest(
    String url,
    String emitKey,
    String pageEmitPayloadType,
    String webResourceEmitPayloadType,
    String assetEmitPayloadType) {

  public static final String EVENT_TYPE = "com.streamx.blueprints.resource-download.request.v1";

}
