package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.ByteBuffer;

/**
 * Represents object containing data to be injected into layout.
 */
public class Composition extends Resource {

  public static final String TYPE_COMPOSITION_PUBLISHED = "com.streamx.blueprints.composition.published.v1";
  public static final String TYPE_COMPOSITION_UNPUBLISHED = "com.streamx.blueprints.composition.unpublished.v1";

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
