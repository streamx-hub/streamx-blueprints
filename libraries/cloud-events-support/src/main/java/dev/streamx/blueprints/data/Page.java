package dev.streamx.blueprints.data;

import java.nio.ByteBuffer;

public class Page extends TypedBinaryResource {

  public static final String TYPE_PUBLISHED = "dev.streamx.blueprints.page.published.v1";
  public static final String TYPE_UNPUBLISHED = "dev.streamx.blueprints.page.unpublished.v1";

  public Page(ByteBuffer content) {
    super(content);
  }

  public Page(byte[] content) {
    super(content);
  }

  public Page(String content) {
    super(content);
  }

  public Page(ByteBuffer content, String type) {
    super(content, type);
  }

  public Page(byte[] content, String type) {
    super(content, type);
  }

  public Page(String content, String type) {
    super(content, type);
  }
}
