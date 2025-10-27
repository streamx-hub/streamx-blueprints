package com.streamx.blueprints.opensearch.sink.index.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record DefaultDocument(
    List<Fragment> fragments,
    String namespace,
    String type,
    @JsonRawValue String payload
) {

}
