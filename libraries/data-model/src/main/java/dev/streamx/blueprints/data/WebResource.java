package dev.streamx.blueprints.data;

import java.nio.ByteBuffer;

/**
 * Represents object which is capable of being served via HTTP.
 */
public class WebResource extends Resource {

  public static final String TYPE_PUBLISHED = "dev.streamx.blueprints.web-resource.published.v1";
  public static final String TYPE_UNPUBLISHED = "dev.streamx.blueprints.web-resource.unpublished.v1";

  public WebResource(ByteBuffer content) {
    super(content);
  }

  public WebResource(byte[] content) {
    super(content);
  }

  public WebResource(String content) {
    super(content);
  }

  public WebResource(ByteBuffer content, String type) {
    super(content, type);
  }

  public WebResource(byte[] content, String type) {
    super(content, type);
  }

  public WebResource(String content, String type) {
    super(content, type);
  }
}
