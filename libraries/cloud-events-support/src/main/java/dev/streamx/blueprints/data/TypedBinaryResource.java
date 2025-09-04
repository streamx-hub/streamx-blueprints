package dev.streamx.blueprints.data;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;

/**
 * Represents object containing content.
 */
public class TypedBinaryResource extends BaseModel {
  private final ByteBuffer content;

  public TypedBinaryResource(ByteBuffer content) {
    this.content = content;
  }

  public TypedBinaryResource(byte[] content) {
    this(ByteBuffer.wrap(content));
  }

  public TypedBinaryResource(String content) {
    this(content.getBytes(UTF_8));
  }

  public TypedBinaryResource(ByteBuffer content, String type) {
    this.content = content;
    this.type = type;
  }

  public TypedBinaryResource(byte[] content, String type) {
    this(ByteBuffer.wrap(content), type);
  }

  public TypedBinaryResource(String content, String type) {
    this(content.getBytes(UTF_8), type);
  }

  public ByteBuffer getContent() {
    return content;
  }

  public String getContentAsString() {
    return new String(content.array(), UTF_8);
  }
}
