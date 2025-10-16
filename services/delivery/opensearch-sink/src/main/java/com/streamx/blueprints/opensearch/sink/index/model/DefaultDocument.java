package com.streamx.blueprints.opensearch.sink.index.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.util.List;

public record DefaultDocument(
    List<Fragment> fragments,
    String namespace,
    String type,
    @JsonRawValue String payload
) {

}
