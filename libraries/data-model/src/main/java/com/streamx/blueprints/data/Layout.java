package com.streamx.blueprints.data;

import java.nio.ByteBuffer;

public class Layout extends Resource {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.layout.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.layout.unpublished.v1";

  public Layout(ByteBuffer content) {
    super(content);
  }

  public Layout(byte[] content) {
    super(content);
  }

  public Layout(String content) {
    super(content);
  }

  public Layout(ByteBuffer content, String type) {
    super(content, type);
  }

  public Layout(byte[] content, String type) {
    super(content, type);
  }

  public Layout(String content, String type) {
    super(content, type);
  }
}
