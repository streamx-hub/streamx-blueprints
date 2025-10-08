package dev.streamx.blueprints.rendering.engine.converter;

import dev.streamx.blueprints.data.RenderingContext;
import org.apache.avro.specific.AvroGenerated;

/**
 * Designed to keep access to previous version of the {@link RenderingContext} in store in case of
 * unpublish using {@link PreservedRenderingContextMessageConverter}.
 */
@AvroGenerated
public class PreservedRenderingContext {

  private RenderingContext renderingContext;

  private PreservedRenderingContext() {
    // needed for Avro serialization
  }

  public PreservedRenderingContext(RenderingContext renderingContext) {
    this.renderingContext = renderingContext;
  }

  public RenderingContext getRenderingContext() {
    return renderingContext;
  }
}
