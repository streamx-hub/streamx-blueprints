package com.streamx.blueprints.data.collector.stores;

import com.streamx.blueprints.data.Data;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record PreservedData(String key, Data data, String eventType) {

}
