package com.streamx.blueprints.opensearch.sink.index.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.OffsetDateTime;

public record Fragment(String key, OffsetDateTime eventTime, @JsonRawValue String payload) {

}
