package com.streamx.blueprints.web.server;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WebResourcesAccessTest extends WebResourcesAccessTestBase {

  @Override
  protected String getExpectedDefaultNamespace() {
    return ""; // empty by default
  }
}
