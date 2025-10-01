package com.streamx.blueprints.data;

public record DownloadRequest(
    String url,
    String publishKey,
    String pagePublishPayloadType,
    String webResourcePublishPayloadType,
    String assetPublishPayloadType) {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.resource-download.request.v1";

}
