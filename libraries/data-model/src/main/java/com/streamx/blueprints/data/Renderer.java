package com.streamx.blueprints.data;

import java.nio.ByteBuffer;

/**
 * Represents object containing information how to render {@link Data}. See
 * {@link RenderingContext}.
 */
public record Renderer(ByteBuffer template) {

  public static final String TYPE_PUBLISHED = "com.streamx.blueprints.renderer.published.v1";
  public static final String TYPE_UNPUBLISHED = "com.streamx.blueprints.renderer.unpublished.v1";

  public Renderer(String content) {
    this(content == null ? null : ByteBuffer.wrap(content.getBytes()));
  }

}
