package com.streamx.blueprints.data;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;

/**
 * Represents configuration defining what should be rendered. Relates to {@link Renderer} and
 * {@link Data}.
 */
@RegisterForReflection
public record RenderingContext(
    String rendererKey,
    @Nullable String dataKeyMatchPattern,
    @Nullable String dataTypeMatchPattern,
    String outputKeyTemplate,
    String outputTypeTemplate,
    OutputFormat outputFormat) {

  public static final String TYPE_PUBLISHED =
      "com.streamx.blueprints.rendering-context.published.v1";
  public static final String TYPE_UNPUBLISHED =
      "com.streamx.blueprints.rendering-context.unpublished.v1";

  public enum OutputFormat {
    PAGE, FRAGMENT
  }
}
