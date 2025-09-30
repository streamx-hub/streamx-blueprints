package com.streamx.blueprints.data;

import java.nio.ByteBuffer;

public class Asset extends Resource {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.asset.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.asset.unpublished.v1";

  public Asset(ByteBuffer content) {
    super(content);
  }

  public Asset(byte[] content) {
    super(content);
  }

  public Asset(ByteBuffer content, String type) {
    super(content, type);
  }

  public Asset(byte[] content, String type) {
    super(content, type);
  }
}
