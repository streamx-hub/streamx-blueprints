package com.streamx.blueprints.web.server.sink;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;

@QuarkusTest
@TestProfile(value = WebResourcesAccessWithNamespaceConfigTest.Configuration.class)
class WebResourcesAccessWithNamespaceConfigTest extends WebResourcesAccessTestBase {

  private static final String TEST_DEFAULT_NAMESPACE = "test-default-namespace";

  @Override
  protected String getExpectedDefaultNamespace() {
    return TEST_DEFAULT_NAMESPACE;
  }

  public static class Configuration implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("streamx.blueprints.web.default-namespace",
          TEST_DEFAULT_NAMESPACE);
    }
  }
}
