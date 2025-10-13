package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.data.Data;

/**
 * Designed to keep access to previous version of the {@link Data} in store in case of unpublish
 * using {@link PreservedDataStore}.
 */
public record PreservedData(Data data, String eventType) {

}
