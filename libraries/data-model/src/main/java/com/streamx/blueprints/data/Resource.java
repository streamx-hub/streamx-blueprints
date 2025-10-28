package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Represents object containing content.
 */
@RegisterForReflection
public class Resource extends Typed {

  @Nullable
  private final ByteBuffer content;

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

  @JsonIgnore
  public byte[] getContentAsBytes() {
    return content == null ? null : content.array();
  }

  private static ByteBuffer wrapBytes(byte[] content) {
    return content == null ? null : ByteBuffer.wrap(content);
  }

  private static byte[] getBytes(String content) {
    return content == null ? null : content.getBytes();
  }

  private static String contentAsString(ByteBuffer content) {
    return content == null ? null : new String(content.array());
  }

  public static boolean isEmpty(Resource resource) {
    return resource == null || resource.content == null;
  }
}
