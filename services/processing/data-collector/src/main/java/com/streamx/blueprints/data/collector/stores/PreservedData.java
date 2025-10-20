package com.streamx.blueprints.data.collector.stores;

import com.streamx.blueprints.data.Data;

public record PreservedData(String key, Data data, String eventType) {

}
