package com.streamx.blueprints.opensearch;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class TypeNotRequiredProfile implements QuarkusTestProfile  {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of("streamx.blueprints.opensearch-sink.type-required", "false");
  }
}
