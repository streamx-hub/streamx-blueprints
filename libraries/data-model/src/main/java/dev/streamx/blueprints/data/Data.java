package dev.streamx.blueprints.data;

import java.nio.ByteBuffer;

public class Data extends Resource {

  public static final String TYPE_PUBLISHED = "dev.streamx.blueprints.data.published.v1";
  public static final String TYPE_UNPUBLISHED = "dev.streamx.blueprints.data.unpublished.v1";

  public Data(ByteBuffer content) {
    super(content);
  }

  public Data(byte[] content) {
    super(content);
  }

  public Data(String content) {
    super(content);
  }

  public Data(ByteBuffer content, String type) {
    super(content, type);
  }

  public Data(byte[] content, String type) {
    super(content, type);
  }

  public Data(String content, String type) {
    super(content, type);
  }
}
