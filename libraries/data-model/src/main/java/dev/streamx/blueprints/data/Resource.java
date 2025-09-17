package dev.streamx.blueprints.data;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;

/**
 * Represents object containing content.
 */
public class Resource extends Typed {
  private final ByteBuffer content;

  public Resource(ByteBuffer content) {
    this.content = content;
  }

  public Resource(byte[] content) {
    this(ByteBuffer.wrap(content));
  }

  public Resource(String content) {
    this(content.getBytes(UTF_8));
  }

  public Resource(ByteBuffer content, String type) {
    this.content = content;
    this.type = type;
  }

  public Resource(byte[] content, String type) {
    this(ByteBuffer.wrap(content), type);
  }

  public Resource(String content, String type) {
    this(content.getBytes(UTF_8), type);
  }

  public ByteBuffer getContent() {
    return content;
  }

  public String getContentAsString() {
    return new String(content.array(), UTF_8);
  }
}
