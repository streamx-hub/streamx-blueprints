package com.streamx.blueprints.rendering.engine;

import com.streamx.blueprints.data.RenderingContext.OutputFormat;

/**
 * This is model dedicated for relay topic used internally by the Rendering Engine to trigger
 * generation of output. Information if the output should be published or unpublished should be
 * passed via event type.
 */
public record RenderingRequest(
    String dataKey,
    String rendererKey,
    String outputKeyTemplate,
    String outputTypeTemplate,
    OutputFormat outputFormat) {

  public static final String TYPE_PUBLISHED =
      "com.streamx.blueprints.rendering-request.published.v1";
  public static final String TYPE_UNPUBLISHED =
      "com.streamx.blueprints.rendering-request.unpublished.v1";
}
