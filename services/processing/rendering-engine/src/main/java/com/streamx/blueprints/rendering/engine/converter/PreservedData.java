package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.Data;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Designed to keep access to previous version of the {@link Data} in store in case of unpublish
 * using {@link PreservedDataStore}.
 */
@RegisterForReflection
public record PreservedData(Data data, String eventType) {

}
