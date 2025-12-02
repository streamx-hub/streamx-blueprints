package com.streamx.blueprints.opensearch.sink.index.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.OffsetDateTime;

@RegisterForReflection
public record Fragment(String key, OffsetDateTime eventTime, @JsonRawValue String payload) {

}
