package dev.streamx.blueprints.opensearch;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class NoOpensearchProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of("quarkus.elasticsearch.devservices.enabled", "false");
  }

  @Override
  public boolean disableApplicationLifecycleObservers() {
    return true;
  }
}
