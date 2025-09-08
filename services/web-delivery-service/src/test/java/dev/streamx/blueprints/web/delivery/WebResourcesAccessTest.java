package dev.streamx.blueprints.web.delivery;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WebResourcesAccessTest extends WebResourcesAccessTestBase {

  @Override
  protected String getExpectedDefaultNamespace() {
    return ""; // empty by default
  }
}
