package dev.streamx.blueprints.opensearch.delivery.rest;

import com.fasterxml.jackson.databind.JsonNode;

public interface SearchResultTransformer<T> {

  T transform(JsonNode jsonNode);
}
