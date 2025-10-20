package com.streamx.blueprints.data.collector;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(value = CollectorTest.Configuration.class)
class CollectorTest extends AbstractCollectorTest {

  protected String getExpectedCollectedDataOutputType() {
    return TestCollector.TEST_OUTPUT_TYPE;
  }

  public static class Configuration implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "collectortest";
    }
  }
}
