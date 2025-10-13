package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.RenderingContext;

/**
 * Designed to keep access to previous version of the {@link RenderingContext} in store in case of
 * unpublish using {@link PreservedRenderingContextStore}.
 */
public record PreservedRenderingContext(RenderingContext renderingContext, String eventType) {

}
