package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.ByteBuffer;

/**
 * Represents an object containing a valid JSON.
 */
public abstract class JsonResource extends Resource {

  public JsonResource(ByteBuffer content) {
    super(content);
  }

  public JsonResource(byte[] content) {
    super(content);
  }

  public JsonResource(String content) {
    super(content);
  }

  @JsonCreator
  public JsonResource(@JsonProperty("content") ByteBuffer content,
      @JsonProperty("type") String type) {
    super(content, type);
  }

  public JsonResource(byte[] content, String type) {
    super(content, type);
  }

  public JsonResource(String content, String type) {
    super(content, type);
  }

}
