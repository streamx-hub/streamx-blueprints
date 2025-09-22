package dev.streamx.blueprints.externalresources.data;

import dev.streamx.blueprints.data.Resource;

public final class ParentResource<T extends Resource> {

  private final String absoluteUrl;
  private final String streamxKey;
  private final String content;
  private final Class<T> type;

  public ParentResource(String absoluteUrl, String streamxKey, String content, Class<T> type) {
    this.absoluteUrl = absoluteUrl;
    this.streamxKey = streamxKey;
    this.content = content;
    this.type = type;
  }

  public String getAbsoluteUrl() {
    return absoluteUrl;
  }

  public String getStreamxKey() {
    return streamxKey;
  }

  public String getContent() {
    return content;
  }

  public Class<T> getType() {
    return type;
  }
}
