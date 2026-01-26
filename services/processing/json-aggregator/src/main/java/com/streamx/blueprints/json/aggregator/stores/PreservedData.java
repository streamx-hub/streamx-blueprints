package com.streamx.blueprints.json.aggregator.stores;

import com.streamx.blueprints.data.Data;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record PreservedData(Data data, String eventType) {

}
