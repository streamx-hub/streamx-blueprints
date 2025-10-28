package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

/**
 * Represents a generic object containing JSON data
 */
@RegisterForReflection
public class Data extends JsonResource {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.data.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.data.unpublished.v1";

  @JsonCreator
  public Data(@JsonProperty("content") ByteBuffer content, @JsonProperty("type") String type) {
    super(content, type);
  }

  public Data(String content, String type) {
    super(content, type);
  }
}
