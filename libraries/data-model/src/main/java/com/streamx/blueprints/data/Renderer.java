package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

/**
 * Represents object containing information how to render {@link Data}. The content field contains
 * the rendering template. See * {@link RenderingContext}.
 */
@RegisterForReflection
public class Renderer extends Resource {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.renderer.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.renderer.unpublished.v1";

  @JsonCreator
  public Renderer(@JsonProperty("content") ByteBuffer content, @JsonProperty("type") String type) {
    super(content, type);
  }

  public Renderer(String content, String type) {
    super(content, type);
  }

}
