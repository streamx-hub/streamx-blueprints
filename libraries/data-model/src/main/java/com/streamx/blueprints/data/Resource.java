package com.streamx.blueprints.data;

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
    this(content, null);
  }

  public Resource(byte[] content) {
    this(wrapBytes(content));
  }

  public Resource(String content) {
    this(getBytes(content));
  }

  @JsonCreator
  public Resource(@JsonProperty("content") ByteBuffer content, @JsonProperty("type") String type) {
    super(type);
    this.content = content;
  }

  public Resource(byte[] content, String type) {
    this(wrapBytes(content), type);
  }

  public Resource(String content, String type) {
    this(getBytes(content), type);
  }

  public ByteBuffer getContent() {
    return content;
  }

  @JsonIgnore
  public String getContentAsString() {
    return contentAsString(content);
  }

  private static ByteBuffer wrapBytes(byte[] content) {
    return content == null ? null : ByteBuffer.wrap(content);
  }

  private static byte[] getBytes(String content) {
    return content == null ? null : content.getBytes(UTF_8);
  }

  private static String contentAsString(ByteBuffer content) {
    return content == null ? null : new String(content.array(), UTF_8);
  }
}
