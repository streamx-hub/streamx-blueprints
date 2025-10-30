package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

/**
 * Represents an object containing a valid JSON.
 */
@RegisterForReflection
public abstract class JsonResource extends Resource {

  @JsonCreator
  public JsonResource(@JsonProperty("content") ByteBuffer content,
      @JsonProperty("type") String type) {
    super(content, type);
  }

  public JsonResource(String content, String type) {
    super(content, type);
  }

}
