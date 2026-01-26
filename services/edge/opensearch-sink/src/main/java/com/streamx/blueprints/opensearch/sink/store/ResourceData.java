package com.streamx.blueprints.opensearch.sink.store;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.OffsetDateTime;

@RegisterForReflection
public record ResourceData(String content, OffsetDateTime eventTime) {

}