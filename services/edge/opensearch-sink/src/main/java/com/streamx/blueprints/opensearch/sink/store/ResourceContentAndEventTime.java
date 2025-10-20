package com.streamx.blueprints.opensearch.sink.store;

import java.time.OffsetDateTime;

public record ResourceContentAndEventTime(String content, OffsetDateTime eventTime) {

}