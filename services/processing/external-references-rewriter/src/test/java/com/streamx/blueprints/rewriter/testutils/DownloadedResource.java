package com.streamx.blueprints.rewriter.testutils;

public record DownloadedResource(String streamxKey, byte[] content) {

  public DownloadedResource(String streamxKey, String content) {
    this(streamxKey, content == null ? null : content.getBytes());
  }

  public String contentAsString() {
    if (content == null) {
      return null;
    }
    return new String(content);
  }
}