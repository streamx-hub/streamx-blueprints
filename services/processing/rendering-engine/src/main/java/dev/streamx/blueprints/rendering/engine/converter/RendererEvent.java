package dev.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.Renderer;

public record RendererEvent(Renderer renderer, String eventType) {

}
