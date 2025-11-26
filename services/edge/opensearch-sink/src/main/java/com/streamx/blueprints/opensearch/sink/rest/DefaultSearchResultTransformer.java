package com.streamx.blueprints.opensearch.sink.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.blueprints.opensearch.sink.config.Configuration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DefaultSearchResultTransformer implements SearchResultTransformer<JsonNode> {

  @Inject
  Configuration configuration;

  @Inject
  JsonPathFilter jsonPathFilter;

  @Override
  public JsonNode transform(JsonNode jsonNode) {
    return jsonPathFilter.filterJson(jsonNode, configuration.allowedJsonPaths());
  }
}
