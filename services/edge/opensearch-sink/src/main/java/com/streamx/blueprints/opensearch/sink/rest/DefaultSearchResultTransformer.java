package com.streamx.blueprints.opensearch.sink.rest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DefaultSearchResultTransformer implements SearchResultTransformer<JsonNode> {

  @ConfigProperty(name = "streamx.blueprints.opensearch-sink.allowed-json-paths")
  List<String> allowedJsonPaths;

  @Inject
  JsonPathFilter jsonPathFilter;

  @Override
  public JsonNode transform(JsonNode jsonNode) {
    return jsonPathFilter.filterJson(jsonNode, allowedJsonPaths);
  }
}
