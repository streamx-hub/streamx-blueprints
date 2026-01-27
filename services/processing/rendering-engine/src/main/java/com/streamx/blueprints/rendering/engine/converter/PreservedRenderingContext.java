package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.RenderingContext;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Designed to keep access to previous version of the {@link RenderingContext} in store in case of
 * unpublish using {@link PreservedRenderingContextStore}.
 */
@RegisterForReflection
public record PreservedRenderingContext(RenderingContext renderingContext, String eventType) {

}
