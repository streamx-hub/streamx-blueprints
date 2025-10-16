package com.streamx.blueprints.opensearch.sink.index.model;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record Fragment(String key, long eventTime, @JsonRawValue String payload) {

}
