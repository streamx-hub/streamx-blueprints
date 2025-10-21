package com.streamx.blueprints.opensearch.sink.rest;

import com.fasterxml.jackson.databind.JsonNode;

public interface SearchResultTransformer<T> {

  T transform(JsonNode jsonNode);
}
