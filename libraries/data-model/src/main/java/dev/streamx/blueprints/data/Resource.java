package dev.streamx.blueprints.data;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonCreator
  public Resource(@JsonProperty("content") ByteBuffer content, @JsonProperty("type") String type) {
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

  @JsonIgnore
  public String getContentAsString() {
    if (content == null) {
      return null;
    }
    return new String(content.array(), UTF_8);
  }
}
