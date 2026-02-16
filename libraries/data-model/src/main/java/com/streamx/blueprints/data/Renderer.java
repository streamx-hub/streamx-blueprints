package com.streamx.blueprints.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.ByteBuffer;

/**
 * Represents object containing information how to render {@link Data}. See
 * {@link RenderingContext}.
 */
@RegisterForReflection
public record Renderer(ByteBuffer template) {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.renderer.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.renderer.unpublished.v1";

  public Renderer(String template) {
    this(template == null ? null : ByteBuffer.wrap(template.getBytes()));
  }

  @JsonIgnore
  public String getTemplateAsString() {
    return template == null ? null : new String(template.array());
  }

}
