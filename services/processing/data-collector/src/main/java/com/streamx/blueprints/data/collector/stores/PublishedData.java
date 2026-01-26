package com.streamx.blueprints.data.collector.stores;

import com.streamx.blueprints.data.Data;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Used to store core data about a Data publish CloudEvent in state repository
 */
@RegisterForReflection
public record PublishedData(String key, Data data, String eventType) {

}
