package com.streamx.blueprints.rewriter.testutils;


import static java.nio.charset.StandardCharsets.UTF_8;

public record DownloadedResource(String streamxKey, byte[] content) {

  public DownloadedResource(String streamxKey, String content) {
    this(streamxKey, content == null ? null : content.getBytes(UTF_8));
  }

  public String contentAsString() {
    if (content == null) {
      return null;
    }
    return new String(content, UTF_8);
  }
}