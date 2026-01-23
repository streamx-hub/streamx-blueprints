package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.Renderer;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record RendererEvent(Renderer renderer, String eventType) {

}
