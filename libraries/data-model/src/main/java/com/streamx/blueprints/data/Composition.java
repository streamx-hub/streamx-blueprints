package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

/**
 * Represents object containing data to be injected into layout.
 */
@RegisterForReflection
public class Composition extends Resource {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.composition.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.composition.unpublished.v1";

  private final String layoutKey;

  @JsonCreator
  public Composition(@JsonProperty("content") ByteBuffer content, @JsonProperty("type") String type,
      @JsonProperty("layoutKey") String layoutKey) {
    super(content, type);
    this.layoutKey = layoutKey;
  }

  public Composition(String content, String type, String layoutKey) {
    super(content, type);
    this.layoutKey = layoutKey;
  }

  public String getLayoutKey() {
    return layoutKey;
  }
}
