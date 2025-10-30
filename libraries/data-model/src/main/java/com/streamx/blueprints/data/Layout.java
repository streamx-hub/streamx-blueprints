package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

@RegisterForReflection
public class Layout extends Resource {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.layout.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.layout.unpublished.v1";

  @JsonCreator
  public Layout(@JsonProperty("content") ByteBuffer content, @JsonProperty("type") String type) {
    super(content, type);
  }

  public Layout(String content, String type) {
    super(content, type);
  }
}
